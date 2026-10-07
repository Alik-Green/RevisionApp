package com.revisionapp.data.sync

import com.revisionapp.crypto.PackHasher
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.InstalledPack
import com.revisionapp.domain.model.PackId
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.Topic
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.repository.PackStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Proves the whole content pipeline against a tiny in-memory sample pack: fetch
 * the manifest, compare versions and hashes, download only what changed, verify
 * the checksum, import. Nothing here touches a real network.
 */
class ContentSyncTest {

    private val packJson = """
        {"packId":"sample","name":"Sample pack","version":1,
         "topics":[{"slug":"root","name":"Root","sortOrder":0},
                   {"slug":"leaf","name":"Leaf","parent":"root","sortOrder":1}],
         "tags":[{"slug":"board-test","group":"board","name":"Test board"}],
         "cardFiles":["leaf.json"]}
    """.trimIndent()

    private val cardJson = """
        {"topic":"leaf","tagRefs":["board-test"],
         "cards":[{"id":"0001","front":"What is B?","back":"B is the answer",
                   "answerType":"TEXT","keyPoints":[{"text":"the answer","mustInclude":true}],
                   "specRef":"1.1"}]}
    """.trimIndent()

    private fun manifest(sha256: String, version: Int = 1, cardCount: Int = 1, schemaVersion: Int = 1): String = """
        {"schemaVersion":$schemaVersion,
         "packs":[{"id":"sample","name":"Sample pack","version":$version,
                   "path":"packs/sample","sha256":"$sha256","cardCount":$cardCount}]}
    """.trimIndent()

    private fun hashOf(pack: String, cards: Map<String, String>): String = PackHasher.hash(pack, cards)

    private fun syncWith(
        files: Map<String, String>,
        store: FakePackStore = FakePackStore(),
    ): Pair<ContentSync, FakePackStore> {
        val http = HttpClient(
            MockEngine { request ->
                val url = request.url.toString()
                val match = files.entries.firstOrNull { url.endsWith(it.key) }
                if (match == null) {
                    respond("not found", HttpStatusCode.NotFound)
                } else {
                    respond(match.value, HttpStatusCode.OK)
                }
            },
        )
        val api = ContentApiClient(http) { "https://example.test/content" }
        return ContentSync(api, store) to store
    }

    @Test
    fun aFreshSyncImportsThePackWithNamespacedIds() = runTest {
        val sha = hashOf(packJson, mapOf("leaf.json" to cardJson))
        val (sync, store) = syncWith(
            mapOf(
                "manifest.json" to manifest(sha),
                "packs/sample/pack.json" to packJson,
                "packs/sample/cards/leaf.json" to cardJson,
            ),
        )

        val result = sync.sync()

        assertIs<SyncResult.Completed>(result)
        assertEquals(emptyList(), result.report.errors.map { it.describe() })
        assertEquals(1, result.report.updated.size)
        assertEquals(CardId("builtin:sample:leaf:0001"), store.importedCards.single().id)
        assertEquals(TopicId("builtin:sample:leaf"), store.importedCards.single().topicId)
        assertEquals(setOf(TagId("builtin:tag:board:board-test")), store.importedCards.single().tagIds)
        assertEquals(
            listOf(TopicId("builtin:sample:root"), TopicId("builtin:sample:leaf")),
            store.importedTopics.map { it.id },
        )
        assertEquals(sha, store.installedPacks.getValue(PackId("sample")).sha256)
    }

    @Test
    fun anUnchangedPackIsNotDownloadedAgain() = runTest {
        val sha = hashOf(packJson, mapOf("leaf.json" to cardJson))
        val store = FakePackStore()
        store.installedPacks[PackId("sample")] = InstalledPack(PackId("sample"), "Sample pack", 1, sha, 1)

        val (sync, _) = syncWith(mapOf("manifest.json" to manifest(sha)), store)
        val result = sync.sync()

        assertIs<SyncResult.Completed>(result)
        assertEquals(1, result.report.unchanged.size)
        assertTrue(result.report.updated.isEmpty())
        assertTrue(store.imports.isEmpty(), "an unchanged pack must not be re-imported")
    }

    @Test
    fun aChecksumMismatchLeavesTheInstalledVersionAlone() = runTest {
        val tamperedCards = cardJson.replace("B is the answer", "something else entirely")
        val realSha = hashOf(packJson, mapOf("leaf.json" to cardJson))
        val store = FakePackStore()
        store.installedPacks[PackId("sample")] = InstalledPack(PackId("sample"), "Sample pack", 1, realSha, 1)

        val (sync, _) = syncWith(
            mapOf(
                // The manifest still advertises the old hash but the card file changed.
                "manifest.json" to manifest(realSha, version = 2),
                "packs/sample/pack.json" to packJson,
                "packs/sample/cards/leaf.json" to tamperedCards,
            ),
            store,
        )
        val result = sync.sync()

        assertIs<SyncResult.Completed>(result)
        assertIs<SyncError.HashMismatch>(result.report.errors.single())
        assertTrue(store.imports.isEmpty())
        assertEquals(realSha, store.installedPacks.getValue(PackId("sample")).sha256)
    }

    @Test
    fun aVersionBumpWithAMatchingHashIsImported() = runTest {
        val bumpedPack = packJson.replace("\"version\":1", "\"version\":2")
        val sha = hashOf(bumpedPack, mapOf("leaf.json" to cardJson))
        val store = FakePackStore()
        store.installedPacks[PackId("sample")] =
            InstalledPack(PackId("sample"), "Sample pack", 1, "0".repeat(64), 1)

        val (sync, _) = syncWith(
            mapOf(
                "manifest.json" to manifest(sha, version = 2),
                "packs/sample/pack.json" to bumpedPack,
                "packs/sample/cards/leaf.json" to cardJson,
            ),
            store,
        )
        val result = sync.sync()

        assertIs<SyncResult.Completed>(result)
        assertEquals(1, result.report.updated.size)
        assertEquals(2, store.installedPacks.getValue(PackId("sample")).version)
    }

    @Test
    fun anUnreachableManifestIsFatalButNeverWipesTheLibrary() = runTest {
        val store = FakePackStore()
        store.installedPacks[PackId("sample")] =
            InstalledPack(PackId("sample"), "Sample pack", 1, "0".repeat(64), 1)

        val (sync, _) = syncWith(emptyMap(), store)
        val result = sync.sync()

        assertIs<SyncResult.Fatal>(result)
        assertIs<SyncError.Network>(result.error)
        assertTrue(store.imports.isEmpty())
        assertEquals(1, store.installedPacks.size)
    }

    @Test
    fun aCardCountMismatchIsRejected() = runTest {
        val sha = hashOf(packJson, mapOf("leaf.json" to cardJson))
        val (sync, store) = syncWith(
            mapOf(
                "manifest.json" to manifest(sha, cardCount = 7),
                "packs/sample/pack.json" to packJson,
                "packs/sample/cards/leaf.json" to cardJson,
            ),
        )
        val result = sync.sync()

        assertIs<SyncResult.Completed>(result)
        assertIs<SyncError.CardCountMismatch>(result.report.errors.single())
        assertTrue(store.imports.isEmpty())
    }

    @Test
    fun aCardFileNamingTheWrongTopicIsRejected() = runTest {
        val wrong = cardJson.replace("\"topic\":\"leaf\"", "\"topic\":\"other\"")
        val sha = hashOf(packJson, mapOf("leaf.json" to wrong))
        val (sync, store) = syncWith(
            mapOf(
                "manifest.json" to manifest(sha),
                "packs/sample/pack.json" to packJson,
                "packs/sample/cards/leaf.json" to wrong,
            ),
        )
        val result = sync.sync()

        assertIs<SyncResult.Completed>(result)
        assertIs<SyncError.CorruptContent>(result.report.errors.single())
        assertTrue(store.imports.isEmpty())
    }

    @Test
    fun aManifestFromTheFutureIsRejected() = runTest {
        val sha = hashOf(packJson, mapOf("leaf.json" to cardJson))
        val (sync, _) = syncWith(mapOf("manifest.json" to manifest(sha, schemaVersion = 99)))

        val result = sync.sync()

        assertIs<SyncResult.Fatal>(result)
        assertIs<SyncError.CorruptContent>(result.error)
    }

    @Test
    fun malformedJsonIsReportedRatherThanThrown() = runTest {
        val (sync, store) = syncWith(mapOf("manifest.json" to "{not json"))

        val result = sync.sync()

        assertIs<SyncResult.Fatal>(result)
        assertIs<SyncError.CorruptContent>(result.error)
        assertTrue(store.imports.isEmpty())
    }

    @Test
    fun aMissingCardFileIsANetworkErrorForThatPackOnly() = runTest {
        val sha = hashOf(packJson, mapOf("leaf.json" to cardJson))
        val (sync, store) = syncWith(
            mapOf(
                "manifest.json" to manifest(sha),
                "packs/sample/pack.json" to packJson,
            ),
        )
        val result = sync.sync()

        assertIs<SyncResult.Completed>(result)
        assertIs<SyncError.Network>(result.report.errors.single())
        assertTrue(store.imports.isEmpty())
    }

    @Test
    fun aFailingImportIsReported() = runTest {
        val sha = hashOf(packJson, mapOf("leaf.json" to cardJson))
        val store = FakePackStore()
        store.throwOnImport = IllegalStateException("disk full")
        val (sync, _) = syncWith(
            mapOf(
                "manifest.json" to manifest(sha),
                "packs/sample/pack.json" to packJson,
                "packs/sample/cards/leaf.json" to cardJson,
            ),
            store,
        )
        val result = sync.sync()

        assertIs<SyncResult.Completed>(result)
        assertIs<SyncError.ImportFailed>(result.report.errors.single())
    }

    @Test
    fun errorMessagesAreReadableOnTheSettingsScreen() = runTest {
        val sha = hashOf(packJson, mapOf("leaf.json" to cardJson))
        val mismatch = SyncError.HashMismatch("sample", sha, "0".repeat(64))

        assertTrue(mismatch.describe().startsWith("Checksum mismatch"))
        assertTrue(SyncError.Network("boom").describe().contains("boom"))
        assertTrue(SyncError.CardCountMismatch("sample", 4, 3).describe().contains("4"))
    }

    private class FakePackStore : PackStore {
        val installedPacks = LinkedHashMap<PackId, InstalledPack>()
        val imports = ArrayList<List<Card>>()
        var importedCards: List<Card> = emptyList()
        var importedTopics: List<Topic> = emptyList()
        var importedTags: List<Tag> = emptyList()
        var throwOnImport: Throwable? = null

        override fun installed(): List<InstalledPack> = installedPacks.values.toList()

        override fun find(id: PackId): InstalledPack? = installedPacks[id]

        override fun import(pack: InstalledPack, topics: List<Topic>, tags: List<Tag>, cards: List<Card>) {
            throwOnImport?.let { throw it }
            installedPacks[pack.id] = pack
            imports += cards
            importedCards = cards
            importedTopics = topics
            importedTags = tags
        }
    }
}
