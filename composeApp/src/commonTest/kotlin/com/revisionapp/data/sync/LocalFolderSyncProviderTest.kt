package com.revisionapp.data.sync

import com.revisionapp.domain.sync.DeviceId
import com.revisionapp.domain.sync.PayloadCodec
import com.revisionapp.domain.sync.PlainPayloadCodec
import com.revisionapp.domain.sync.RecordId
import com.revisionapp.domain.sync.RecordType
import com.revisionapp.domain.sync.SyncEngine
import com.revisionapp.domain.sync.SyncFailure
import com.revisionapp.domain.sync.SyncLayout
import com.revisionapp.domain.sync.SyncManifest
import com.revisionapp.domain.sync.SyncRecord
import com.revisionapp.domain.sync.SyncStore
import com.revisionapp.platform.SyncStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The folder transport, exercised through a [SyncStorage] that keeps files in a
 * map. What is being proved is not that bytes move -- it is that a folder with
 * no API behind it is still a safe place to keep a library: unreadable files are
 * skipped rather than fatal, ids never reach the disk with characters a
 * filesystem rejects, and two devices pointed at the same folder converge.
 */
class LocalFolderSyncProviderTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val codec: PayloadCodec = PlainPayloadCodec
    private val deviceA = DeviceId("dev-a")
    private val deviceB = DeviceId("dev-b")

    @Test
    fun aRecordWrittenIsListedAndReadBackUnchanged() = runTest {
        val provider = provider(FolderStorage("Sync"))
        val record = card("user:1", NOW, deviceA, "{\"front\":\"What is a mole?\"}")

        provider.putRecord(record)
        val headers = provider.listHeaders(RecordType.CARD)
        val fetched = provider.getRecord(RecordType.CARD, record.id)

        assertEquals(1, headers.size)
        assertEquals(record.header, headers.first())
        assertEquals(record, fetched)
    }

    @Test
    fun anUnusedFolderListsEmptyRatherThanFailing() = runTest {
        val provider = provider(FolderStorage("Sync"))

        assertEquals(emptyList(), provider.listHeaders(RecordType.CARD))
        assertEquals(emptyList(), provider.listLogDevices())
        assertEquals(emptyList(), provider.listLogSegments(deviceA))
        assertEquals(emptyList(), provider.readLogSegment(deviceA, SyncLayout.segmentFor(2026, 10)))
        assertNull(provider.readManifest())
        assertNull(provider.getRecord(RecordType.CARD, RecordId("user:nope")))
    }

    @Test
    fun theManifestRoundTrips() = runTest {
        val provider = provider(FolderStorage("Sync"))
        val manifest = SyncManifest(SyncLayout.SCHEMA_VERSION, codec.id, deviceA, NOW)

        provider.writeManifest(manifest)

        assertEquals(manifest, provider.readManifest())
    }

    @Test
    fun idsNeverReachTheDiskWithCharactersAFilesystemRejects() = runTest {
        // ':' is illegal in a Windows filename and user ids are 'user:<uuid>'.
        val storage = FolderStorage("Sync")
        val provider = provider(storage)
        val record = card("user:2f1c|9a7e", NOW, deviceA, "{}")

        provider.putRecord(record)

        val paths = storage.list("records/")
        assertEquals(1, paths.size)
        assertFalse(paths.first().contains(':'), paths.first())
        assertFalse(paths.first().contains('|'), paths.first())
        assertEquals(record, provider.getRecord(RecordType.CARD, record.id), "and it still reads back")
    }

    @Test
    fun aCorruptOrHalfWrittenRecordIsSkippedNotFatal() = runTest {
        val storage = FolderStorage("Sync")
        val provider = provider(storage)
        provider.putRecord(card("user:good", NOW, deviceA, "{}"))
        storage.writeBytes("records/card/us/user%3abad.json", "not json at all".encodeToByteArray())
        storage.writeBytes("records/card/us/user%3aempty.json", ByteArray(0))

        val headers = provider.listHeaders(RecordType.CARD)

        assertEquals(1, headers.size, "only the readable record is listed")
        assertEquals(RecordId("user:good"), headers.first().id)
    }

    @Test
    fun aRecordTypeFromANewerBuildIsSkipped() = runTest {
        val storage = FolderStorage("Sync")
        val provider = provider(storage)
        provider.putRecord(card("user:1", NOW, deviceA, "{}"))
        storage.writeBytes(
            "records/card/ca/card-from-2029.json",
            """{"id":"user:9","type":"a-type-that-does-not-exist-yet","deviceId":"dev-z"}"""
                .encodeToByteArray(),
        )

        assertEquals(1, provider.listHeaders(RecordType.CARD).size)
    }

    @Test
    fun deletingARecordRemovesItsFile() = runTest {
        val storage = FolderStorage("Sync")
        val provider = provider(storage)
        val record = card("user:1", NOW, deviceA, "{}")
        provider.putRecord(record)

        provider.deleteRecord(RecordType.CARD, record.id)

        assertNull(provider.getRecord(RecordType.CARD, record.id))
        assertEquals(emptyList(), provider.listHeaders(RecordType.CARD))
    }

    @Test
    fun logSegmentsAppendAndReadBackInOrder() = runTest {
        val provider = provider(FolderStorage("Sync"))
        val segment = SyncLayout.segmentFor(2026, 10)

        provider.appendLogSegment(deviceA, segment, listOf("{\"r\":1}"))
        provider.appendLogSegment(deviceA, segment, listOf("{\"r\":2}", "{\"r\":3}"))
        provider.appendLogSegment(deviceB, segment, listOf("{\"other\":1}"))

        assertEquals(listOf("{\"r\":1}", "{\"r\":2}", "{\"r\":3}"), provider.readLogSegment(deviceA, segment))
        assertEquals(listOf(segment), provider.listLogSegments(deviceA))
        assertEquals(listOf(deviceA, deviceB), provider.listLogDevices(), "sorted, and both found")
        assertEquals(emptyList(), provider.readLogSegment(deviceA, SyncLayout.segmentFor(2026, 11)))
    }

    @Test
    fun aSegmentFromAnUnknownDeviceIsStillFoundByTheListing() = runTest {
        // The device that has never synced with this one is exactly the one
        // whose reviews would otherwise be dropped from the rebuilt schedule.
        val storage = FolderStorage("Sync")
        storage.writeBytes("log/stranger/2026-10.jsonl", "{\"r\":1}\n".encodeToByteArray())
        val provider = provider(storage)

        assertEquals(listOf(DeviceId("stranger")), provider.listLogDevices())
        assertEquals(listOf("{\"r\":1}"), provider.readLogSegment(DeviceId("stranger"), SyncLayout.segmentFor(2026, 10)))
    }

    @Test
    fun aTransportFailureIsReportedByTheEngineRatherThanThrownAtTheUser() = runTest {
        val storage = FolderStorage("Sync")
        val store = MapStore(deviceA)
        store.put(card("user:1", NOW, deviceA, "{}"))
        storage.failOn = "writeBytes"

        val report = SyncEngine(provider(storage), store, codec, Clock.System, { NOW }).sync()

        assertFalse(report.succeeded)
        assertTrue(report.failure is SyncFailure.Storage, "was " + report.failure)
        assertNull(provider(storage).readManifest(), "a failed run must not claim to have synced")
    }

    @Test
    fun twoDevicesPointedAtTheSameFolderConverge() = runTest {
        // The end-to-end property the rest of this file exists to protect.
        val folder = FolderStorage("Shared")
        val storeA = MapStore(deviceA)
        val storeB = MapStore(deviceB)
        storeA.put(card("user:a", NOW, deviceA, "{\"front\":\"from A\"}"))
        storeB.put(card("user:b", NOW, deviceB, "{\"front\":\"from B\"}"))

        SyncEngine(provider(folder), storeA, codec, Clock.System, { NOW }).sync()
        val second = SyncEngine(provider(folder), storeB, codec, Clock.System, { NOW + 1 }).sync()
        val third = SyncEngine(provider(folder), storeA, codec, Clock.System, { NOW + 2 }).sync()

        assertTrue(second.succeeded, "failure was " + second.failure)
        assertTrue(third.succeeded, "failure was " + third.failure)
        assertEquals(
            storeA.localRecords(RecordType.CARD).map { it.id }.toSet(),
            storeB.localRecords(RecordType.CARD).map { it.id }.toSet(),
            "both devices hold the same library",
        )
        assertEquals(2, storeA.localRecords(RecordType.CARD).size)
        assertEquals("{\"front\":\"from A\"}", storeA.payloadOf("user:a"))
        assertEquals("{\"front\":\"from B\"}", storeA.payloadOf("user:b"))
        assertEquals(third.uploaded, 0, "by the third run there is nothing left to do")
    }

    @Test
    fun anEditOnOneDeviceWinsOverAnOlderCopyOnTheOther() = runTest {
        val folder = FolderStorage("Shared")
        val storeA = MapStore(deviceA)
        val storeB = MapStore(deviceB)
        storeA.put(card("user:1", NOW, deviceA, "{\"front\":\"original\"}"))
        SyncEngine(provider(folder), storeA, codec, Clock.System, { NOW }).sync()

        SyncEngine(provider(folder), storeB, codec, Clock.System, { NOW + 1 }).sync()
        storeB.put(card("user:1", NOW + 5_000, deviceB, "{\"front\":\"edited on B\"}"))
        SyncEngine(provider(folder), storeB, codec, Clock.System, { NOW + 6 }).sync()
        val final = SyncEngine(provider(folder), storeA, codec, Clock.System, { NOW + 7 }).sync()

        assertTrue(final.succeeded)
        assertEquals("{\"front\":\"edited on B\"}", storeA.payloadOf("user:1"))
        assertEquals(1, final.conflicts.size, "A had a copy, so the overwrite is logged")
        assertEquals(deviceB, final.conflicts.first().keptDevice)
    }

    @Test
    fun aDeleteOnOneDeviceRemovesTheCardOnTheOther() = runTest {
        val folder = FolderStorage("Shared")
        val storeA = MapStore(deviceA)
        val storeB = MapStore(deviceB)
        storeA.put(card("user:1", NOW, deviceA, "{}"))
        SyncEngine(provider(folder), storeA, codec, Clock.System, { NOW }).sync()
        SyncEngine(provider(folder), storeB, codec, Clock.System, { NOW + 1 }).sync()
        assertEquals(1, storeB.localRecords(RecordType.CARD).size)

        storeA.put(SyncRecord.tombstone(RecordId("user:1"), RecordType.CARD, NOW + 5_000, deviceA))
        SyncEngine(provider(folder), storeA, codec, Clock.System, { NOW + 6 }).sync()
        SyncEngine(provider(folder), storeB, codec, Clock.System, { NOW + 7 }).sync()

        assertTrue(storeB.recordOf("user:1", RecordType.CARD)?.deleted == true, "the delete propagates")
    }

    private fun provider(storage: SyncStorage) = LocalFolderSyncProvider(storage, json, codec)

    private fun card(id: String, updatedAt: Long, device: DeviceId, payload: String) = SyncRecord(
        id = RecordId(id),
        type = RecordType.CARD,
        updatedAt = updatedAt,
        deviceId = device,
        payloadHash = payload.hashCode().toString(16),
        deleted = false,
        payload = payload,
    )

    private companion object {
        const val NOW: Long = 1_760_000_000_000L
    }

    /** A folder in a map, with a switch to make one verb fail. */
    private class FolderStorage(override val label: String) : SyncStorage {
        val files = sortedMapOf<String, ByteArray>()
        var failOn: String? = null

        override val supportsAtomicRename: Boolean = true

        private fun guard(verb: String) {
            if (failOn == verb) throw IllegalStateException("the disk is full")
        }

        override suspend fun list(prefix: String): List<String> {
            guard("list")
            return files.keys.filter { it.startsWith(prefix) }.toList()
        }

        override suspend fun readBytes(path: String): ByteArray? {
            guard("readBytes")
            return files[path]
        }

        override suspend fun writeBytes(path: String, bytes: ByteArray) {
            guard("writeBytes")
            files[path] = bytes
        }

        override suspend fun delete(path: String) {
            guard("delete")
            files.remove(path)
        }
    }

    /** The local half of a sync, as a map. */
    private class MapStore(override val deviceId: DeviceId) : SyncStore {
        private val records = mutableMapOf<RecordType, MutableMap<RecordId, SyncRecord>>()

        fun put(record: SyncRecord) {
            records.getOrPut(record.type) { mutableMapOf() }[record.id] = record
        }

        fun recordOf(id: String, type: RecordType): SyncRecord? = records[type]?.get(RecordId(id))

        fun payloadOf(id: String): String? = recordOf(id, RecordType.CARD)?.payload

        override suspend fun localRecords(type: RecordType): List<SyncRecord> =
            records[type]?.values?.toList() ?: emptyList()

        override suspend fun applyDownloaded(newRecords: List<SyncRecord>) {
            for (record in newRecords) put(record)
        }

        override suspend fun markUploaded(newRecords: List<SyncRecord>) = Unit

        override suspend fun forgetTombstones(ids: List<RecordId>) {
            for (type in RecordType.entries) for (id in ids) records[type]?.remove(id)
        }

        override suspend fun repairOrphanedCards(): Int = 0
    }
}
