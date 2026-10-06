package com.revisionapp.data.sync

import com.revisionapp.crypto.PackHasher
import com.revisionapp.data.AppJson
import com.revisionapp.data.content.CardFileDto
import com.revisionapp.data.content.ContentManifestDto
import com.revisionapp.data.content.ContentMapper
import com.revisionapp.data.content.PackDto
import com.revisionapp.data.content.PackSummaryDto
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.InstalledPack
import com.revisionapp.domain.model.PackId
import com.revisionapp.domain.repository.PackStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/** Raised when a content file cannot be downloaded. */
class ContentFetchException(val url: String, val statusCode: Int) :
    Exception("HTTP $statusCode for $url")

/** Fetches raw files from the `content` branch. */
class ContentApiClient(
    private val http: HttpClient,
    private val baseUrl: () -> String,
) {
    /** The response body exactly as served, so that hashing sees the real bytes. */
    suspend fun fetch(relativePath: String): String {
        val url = baseUrl().trimEnd('/') + "/" + relativePath.removePrefix("/")
        val response = http.get(url)
        if (!response.status.isSuccess()) {
            throw ContentFetchException(url, response.status.value)
        }
        return response.bodyAsText()
    }
}

/** What went wrong with one pack, or with the manifest as a whole. */
sealed interface SyncError {
    data class Network(val message: String) : SyncError
    data class CorruptContent(val message: String) : SyncError
    data class HashMismatch(val packId: String, val expected: String, val actual: String) : SyncError
    data class CardCountMismatch(val packId: String, val expected: Int, val actual: Int) : SyncError
    data class ImportFailed(val packId: String, val message: String) : SyncError

    /** One line for the settings screen. */
    fun describe(): String = when (this) {
        is Network -> "Network: $message"
        is CorruptContent -> "Malformed content: $message"
        is HashMismatch -> "Checksum mismatch for '$packId' (expected ${expected.takeShort()}, got ${actual.takeShort()})"
        is CardCountMismatch -> "'$packId' declares $expected cards but $actual were delivered"
        is ImportFailed -> "Import failed for '$packId': $message"
    }
}

private fun String.takeShort(): String = if (length <= 12) this else take(12) + "..."

/** What one pack download ended up doing. */
data class PackSyncResult(
    val pack: InstalledPack?,
    val error: SyncError?,
)

/** The result of a whole sync run. */
data class SyncReport(
    val updated: List<InstalledPack>,
    val unchanged: List<InstalledPack>,
    val errors: List<SyncError>,
) {
    val isSuccess: Boolean get() = errors.isEmpty()
    val touchedAnything: Boolean get() = updated.isNotEmpty()
}

/**
 * Downloads the manifest, compares pack versions and hashes, fetches only what
 * changed, verifies the checksum and imports it in one transaction.
 *
 * A pack that fails any check is simply left alone: whatever version is already
 * in the database stays put, and the app keeps working offline. Nothing here can
 * reach user content or study progress — see docs/DECISIONS.md D14 and D15.
 */
class ContentSync(
    private val api: ContentApiClient,
    private val packStore: PackStore,
    private val json: Json = AppJson.instance,
) {

    /** Runs a full sync. Never throws: every failure is reported instead. */
    suspend fun sync(): SyncResult {
        val manifestText = try {
            api.fetch(MANIFEST_PATH)
        } catch (error: Throwable) {
            return SyncResult.Fatal(SyncError.Network(describe(error)))
        }

        val manifest = try {
            json.decodeFromString(ContentManifestDto.serializer(), manifestText)
        } catch (error: Throwable) {
            return SyncResult.Fatal(SyncError.CorruptContent(describe(error)))
        }

        if (manifest.schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            return SyncResult.Fatal(
                SyncError.CorruptContent(
                    "manifest schema version ${manifest.schemaVersion} is newer than the " +
                        "$SUPPORTED_SCHEMA_VERSION this build understands",
                ),
            )
        }

        val updated = ArrayList<InstalledPack>()
        val unchanged = ArrayList<InstalledPack>()
        val errors = ArrayList<SyncError>()

        for (summary in manifest.packs) {
            val packId = PackId(summary.id)
            val installed = packStore.find(packId)
            if (installed != null && installed.isCurrent(summary)) {
                unchanged += installed
                continue
            }
            val result = downloadAndImport(summary)
            val pack = result.pack
            if (pack != null) {
                updated += pack
            } else {
                val error = result.error
                if (error != null) errors += error
            }
        }

        return SyncResult.Completed(SyncReport(updated, unchanged, errors))
    }

    private fun InstalledPack.isCurrent(summary: PackSummaryDto): Boolean =
        version == summary.version && sha256.equals(summary.sha256, ignoreCase = true)

    private suspend fun downloadAndImport(summary: PackSummaryDto): PackSyncResult {
        val packId = PackId(summary.id)

        val packJson = try {
            api.fetch("${summary.path}/$PACK_FILE")
        } catch (error: Throwable) {
            return PackSyncResult(null, SyncError.Network(describe(error)))
        }
        val packDto = try {
            json.decodeFromString(PackDto.serializer(), packJson)
        } catch (error: Throwable) {
            return PackSyncResult(null, SyncError.CorruptContent("${summary.id}/$PACK_FILE: ${describe(error)}"))
        }
        if (packDto.packId != summary.id) {
            return PackSyncResult(
                null,
                SyncError.CorruptContent("$PACK_FILE declares '${packDto.packId}' but the manifest says '${summary.id}'"),
            )
        }

        val cardFiles = LinkedHashMap<String, String>()
        for (fileName in packDto.cardFiles) {
            val text = try {
                api.fetch("${summary.path}/$CARDS_DIRECTORY/$fileName")
            } catch (error: Throwable) {
                return PackSyncResult(null, SyncError.Network(describe(error)))
            }
            cardFiles[fileName] = text
        }

        // Verify before touching the database: a truncated or tampered download
        // must leave the previously installed version untouched.
        val actualHash = PackHasher.hash(packJson, cardFiles)
        if (!actualHash.equals(summary.sha256, ignoreCase = true)) {
            return PackSyncResult(null, SyncError.HashMismatch(summary.id, summary.sha256, actualHash))
        }

        val cards = ArrayList<Card>()
        for ((fileName, text) in cardFiles) {
            val fileDto = try {
                json.decodeFromString(CardFileDto.serializer(), text)
            } catch (error: Throwable) {
                return PackSyncResult(null, SyncError.CorruptContent("$fileName: ${describe(error)}"))
            }
            val expectedTopic = fileName.removeSuffix(".json")
            if (fileDto.topic != expectedTopic) {
                return PackSyncResult(
                    null,
                    SyncError.CorruptContent("$fileName declares topic '${fileDto.topic}', expected '$expectedTopic'"),
                )
            }
            cards += ContentMapper.cards(packId, packDto, fileDto)
        }

        if (cards.size != summary.cardCount) {
            return PackSyncResult(null, SyncError.CardCountMismatch(summary.id, summary.cardCount, cards.size))
        }
        if (cards.map { it.id }.toSet().size != cards.size) {
            return PackSyncResult(null, SyncError.CorruptContent("duplicate card ids in pack '${summary.id}'"))
        }
        val declaredTopics = ContentMapper.topics(packId, packDto).map { it.id }.toSet()
        val orphan = cards.firstOrNull { it.topicId !in declaredTopics }
        if (orphan != null) {
            return PackSyncResult(
                null,
                SyncError.CorruptContent("card '${orphan.id.value}' references undeclared topic '${orphan.topicId.value}'"),
            )
        }

        val installed = InstalledPack(
            id = packId,
            name = packDto.name,
            version = packDto.version,
            sha256 = actualHash,
            cardCount = cards.size,
        )
        return try {
            packStore.import(installed, ContentMapper.topics(packId, packDto), ContentMapper.tags(packDto), cards)
            PackSyncResult(installed, null)
        } catch (error: Throwable) {
            PackSyncResult(null, SyncError.ImportFailed(summary.id, describe(error)))
        }
    }

    private fun describe(error: Throwable): String = error.message ?: error.toString()

    companion object {
        const val MANIFEST_PATH: String = "manifest.json"
        const val PACK_FILE: String = "pack.json"
        const val CARDS_DIRECTORY: String = "cards"
        const val SUPPORTED_SCHEMA_VERSION: Int = 1

        /**
         * The public raw-GitHub URL of this repository's `content` branch. A
         * private repository would need a token; see the README.
         */
        const val DEFAULT_BASE_URL: String = "https://raw.githubusercontent.com/Alik-Green/RevisionApp/content"
    }
}

/** The outcome of a sync run, as consumed by the settings screen. */
sealed interface SyncResult {
    /** The manifest was read; per-pack problems, if any, are inside the report. */
    data class Completed(val report: SyncReport) : SyncResult

    /** Nothing could be checked at all. The local library is untouched. */
    data class Fatal(val error: SyncError) : SyncResult
}
