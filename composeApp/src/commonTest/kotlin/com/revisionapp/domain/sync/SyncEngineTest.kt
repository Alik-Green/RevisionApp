package com.revisionapp.domain.sync

import com.revisionapp.data.sync.InMemorySyncProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The merge rules, driven against a real [SyncProvider] implementation rather
 * than a mock that has been told what to say.
 *
 * What is being protected here is not "does it copy files" -- it is the set of
 * properties that make copying files between two devices safe at all: the same
 * two copies produce the same verdict on both devices, a second run does
 * nothing, a delete cannot be resurrected by an older copy, a failure part way
 * through leaves something repeatable, and a topic deletion never takes a
 * card's content with it.
 */
class SyncEngineTest {

    private val deviceA = DeviceId("dev-a")
    private val deviceB = DeviceId("dev-b")

    @Test
    fun aRecordOnlyOnThisDeviceIsUploaded() = runTest {
        val provider = InMemorySyncProvider()
        val store = FakeStore(deviceA)
        store.put(live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertTrue(report.succeeded, "failure was " + report.failure)
        assertEquals(1, report.uploaded)
        assertEquals(0, report.downloaded)
        assertEquals(1, provider.stored(RecordType.CARD).size)
        assertFalse(report.conflicts.any(), "a first upload is not a conflict")
    }

    @Test
    fun aRecordOnlyOnTheRemoteIsDownloaded() = runTest {
        val provider = InMemorySyncProvider()
        provider.seed(live("user:2", RecordType.TOPIC, NOW, deviceB, "{\"name\":\"Mechanics\"}"))
        val store = FakeStore(deviceA)

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertTrue(report.succeeded)
        assertEquals(1, report.downloaded)
        assertEquals(0, report.uploaded)
        assertEquals(1, store.localRecords(RecordType.TOPIC).size)
        assertEquals(1, store.downloadBatches, "downloads are applied in one transaction")
    }

    @Test
    fun aSecondRunIsANoOp() = runTest {
        // The idempotency guarantee. Without it every sync re-uploads the whole
        // library, which is the difference between a usable feature and one that
        // melts a phone's battery and a cloud quota.
        val provider = InMemorySyncProvider()
        val store = FakeStore(deviceA)
        store.put(live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD))
        val engine = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW })

        engine.sync()
        val second = engine.sync()

        assertEquals(0, second.uploaded, "nothing should need uploading again")
        assertEquals(0, second.downloaded)
        assertEquals(0, second.conflicts.size)
        assertEquals(1, provider.storedCount())
    }

    @Test
    fun theNewerCopyWins() = runTest {
        val provider = InMemorySyncProvider()
        provider.seed(live("user:1", RecordType.CARD, NOW + 1_000, deviceB, "{\"front\":\"edited later\"}"))
        val store = FakeStore(deviceA)
        store.put(live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertEquals(1, report.downloaded)
        assertEquals(0, report.uploaded)
        assertEquals("{\"front\":\"edited later\"}", store.payloadOf("user:1", RecordType.CARD))
        assertEquals(1, report.conflicts.size, "both sides had a copy, so this is logged")
        assertEquals(deviceB, report.conflicts.first().keptDevice)
        assertEquals(deviceA, report.conflicts.first().discardedDevice)
    }

    @Test
    fun anOlderRemoteCopyLosesToANewerLocalOne() = runTest {
        val provider = InMemorySyncProvider()
        provider.seed(live("user:1", RecordType.CARD, NOW - 1_000, deviceB, "{\"front\":\"stale\"}"))
        val store = FakeStore(deviceA)
        store.put(live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertEquals(1, report.uploaded)
        assertEquals(0, report.downloaded)
        assertEquals(CARD_PAYLOAD, provider.stored(RecordType.CARD).first().payload)
        assertEquals(deviceA, report.conflicts.first().keptDevice)
    }

    @Test
    fun equalTimestampsAreBrokenByDeviceId() {
        val local = live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD)
        val remote = live("user:1", RecordType.CARD, NOW, deviceB, "{\"front\":\"same instant\"}")

        val decision = LastWriteWins.decide(local, remote.header)

        // "dev-b" sorts after "dev-a", so the remote wins the tiebreak.
        assertEquals(MergeAction.DOWNLOAD, decision.action)
        assertTrue(decision.contested)
        assertTrue(decision.reason.contains("tiebreak"), decision.reason)
    }

    @Test
    fun bothDevicesReachTheSameTiebreakVerdict() {
        // The property that stops a record ping-ponging: whichever device runs
        // the merge, the same copy survives.
        val onA = live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD)
        val onB = live("user:1", RecordType.CARD, NOW, deviceB, "{\"front\":\"same instant\"}")

        val fromA = LastWriteWins.decide(onA, onB.header)
        val fromB = LastWriteWins.decide(onB, onA.header)

        assertEquals(MergeAction.DOWNLOAD, fromA.action, "A sees B as the winner")
        assertEquals(MergeAction.UPLOAD, fromB.action, "B sees itself as the winner")
    }

    @Test
    fun identicalContentIsInSyncEvenWhenTimestampsDisagree() {
        val local = live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD)
        val remote = live("user:1", RecordType.CARD, NOW + 99_999, deviceB, CARD_PAYLOAD)

        val decision = LastWriteWins.decide(local, remote.header)

        assertEquals(MergeAction.NOTHING, decision.action)
        assertFalse(decision.contested, "nothing was discarded, so there is nothing to log")
    }

    @Test
    fun aRemoteTombstoneBeatsAnOlderLocalEdit() = runTest {
        val provider = InMemorySyncProvider()
        provider.seed(SyncRecord.tombstone(RecordId("user:1"), RecordType.CARD, NOW + 1_000, deviceB))
        val store = FakeStore(deviceA)
        store.put(live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertEquals(1, report.downloaded)
        assertTrue(store.recordOf("user:1", RecordType.CARD)?.deleted == true, "the delete must win")
    }

    @Test
    fun aLocalEditNewerThanARemoteTombstoneResurrectsTheCard() = runTest {
        // Delete-versus-edit, the case that loses data if it is got wrong: the
        // edit is newer, so the card comes back rather than staying deleted.
        val provider = InMemorySyncProvider()
        provider.seed(SyncRecord.tombstone(RecordId("user:1"), RecordType.CARD, NOW - 1_000, deviceB))
        val store = FakeStore(deviceA)
        store.put(live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertEquals(1, report.uploaded)
        assertEquals(0, report.downloaded)
        assertFalse(provider.stored(RecordType.CARD).first().deleted)
    }

    @Test
    fun tombstonesOlderThanNinetyDaysAreCompactedOnBothSides() = runTest {
        val cutoff = NOW - SyncRecord.TOMBSTONE_RETENTION_DAYS.toLong() * DAY
        val provider = InMemorySyncProvider()
        provider.seed(SyncRecord.tombstone(RecordId("user:old"), RecordType.CARD, cutoff - 1, deviceB))
        provider.seed(SyncRecord.tombstone(RecordId("user:fresh"), RecordType.CARD, cutoff + 1, deviceB))
        val store = FakeStore(deviceA)
        store.put(SyncRecord.tombstone(RecordId("user:old"), RecordType.CARD, cutoff - 1, deviceA))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertEquals(1, report.compacted)
        assertNull(provider.stored(RecordType.CARD).firstOrNull { it.id.value == "user:old" })
        assertNotNull(provider.stored(RecordType.CARD).firstOrNull { it.id.value == "user:fresh" })
        assertNull(store.recordOf("user:old", RecordType.CARD), "the local copy goes too")
    }

    @Test
    fun aNewerSchemaIsRefusedBeforeAnythingIsWritten() = runTest {
        val provider = InMemorySyncProvider()
        provider.writeManifest(
            SyncManifest(SyncLayout.SCHEMA_VERSION + 1, PlainPayloadCodec.id, deviceB, NOW),
        )
        provider.seed(live("user:1", RecordType.CARD, NOW, deviceB, CARD_PAYLOAD))
        val store = FakeStore(deviceA)
        store.put(live("user:2", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertFalse(report.succeeded)
        val failure = assertIs<SyncFailure.SchemaTooNew>(report.failure)
        assertEquals(0, report.uploaded, "nothing may be written to a location we cannot read")
        assertEquals(0, report.downloaded)
        assertEquals(1, provider.storedCount(), "the remote is untouched")
        assertTrue(failure.message.contains("Update the app"), failure.message)
    }

    @Test
    fun anOlderSchemaIsAccepted() = runTest {
        val provider = InMemorySyncProvider()
        provider.writeManifest(SyncManifest(SyncLayout.SCHEMA_VERSION, PlainPayloadCodec.id, deviceB, NOW))

        val report = SyncEngine(provider, store = FakeStore(deviceA), codec = PlainPayloadCodec, clock = Clock.System, nowMillis = { NOW }).sync()

        assertTrue(report.succeeded, "failure was " + report.failure)
    }

    @Test
    fun aDifferentCodecIsRefused() = runTest {
        val provider = InMemorySyncProvider()
        provider.writeManifest(SyncManifest(SyncLayout.SCHEMA_VERSION, "aes-gcm", deviceB, NOW))
        val store = FakeStore(deviceA)

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertFalse(report.succeeded)
        val failure = assertIs<SyncFailure.CodecMismatch>(report.failure)
        assertEquals("aes-gcm", failure.remoteCodec)
        assertEquals(PlainPayloadCodec.id, failure.localCodec)
        assertTrue(failure.message.contains("aes-gcm"), failure.message)
    }

    @Test
    fun aTransportFailureAbortsTheRunAndLeavesTheManifestAlone() = runTest {
        val provider = InMemorySyncProvider()
        provider.fault = { verb -> if (verb == "listHeaders") SyncFailure.Unreachable("no such folder") else null }
        val store = FakeStore(deviceA)
        store.put(live("user:1", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertFalse(report.succeeded)
        assertTrue(report.failure is SyncFailure.Unreachable)
        assertEquals(0, provider.storedCount(), "nothing was written")
        assertNull(provider.readManifest(), "a failed run must not claim to have synced")
    }

    @Test
    fun anUploadThatDiesHalfwayKeepsWhatLandedAndIsSafeToRepeat() = runTest {
        // Crash safety: the second put fails. What landed stays landed, the
        // manifest does not move, and re-running completes without duplicating.
        val provider = InMemorySyncProvider()
        val store = FakeStore(deviceA)
        store.put(live("user:1", RecordType.CARD, NOW, deviceA, "{\"front\":\"one\"}"))
        store.put(live("user:2", RecordType.CARD, NOW, deviceA, "{\"front\":\"two\"}"))
        var puts = 0
        provider.fault = { verb ->
            if (verb == "putRecord" && ++puts == 2) SyncFailure.Unreachable("disk full") else null
        }

        val failed = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()
        assertFalse(failed.succeeded)
        assertNull(provider.readManifest())
        val landed = provider.stored(RecordType.CARD).size
        assertTrue(landed >= 1, "at least the first record should have landed, got $landed")

        provider.fault = null
        val retried = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertTrue(retried.succeeded, "failure was " + retried.failure)
        assertEquals(2, provider.stored(RecordType.CARD).size, "no duplicates, nothing lost")
        assertNotNull(provider.readManifest())
    }

    @Test
    fun aRecordThatIsListedButGoneIsSkipped() = runTest {
        // What an eventually consistent listing, or a concurrent delete on the
        // other device, actually looks like.
        val provider = InMemorySyncProvider()
        provider.seed(live("user:ghost", RecordType.CARD, NOW, deviceB, CARD_PAYLOAD))
        provider.hiddenIds += RecordId("user:ghost")
        provider.seed(live("user:real", RecordType.CARD, NOW, deviceB, "{\"front\":\"here\"}"))
        val store = FakeStore(deviceA)

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertTrue(report.succeeded, "failure was " + report.failure)
        assertEquals(1, report.downloaded, "the ghost is skipped, not an error")
        assertEquals(1, store.localRecords(RecordType.CARD).size)
    }

    @Test
    fun cardsOrphanedByATopicDeletionAreReparentedNotDeleted() = runTest {
        val provider = InMemorySyncProvider()
        provider.seed(SyncRecord.tombstone(RecordId("topic:gone"), RecordType.TOPIC, NOW + 1_000, deviceB))
        val store = FakeStore(deviceA)
        store.put(live("topic:gone", RecordType.TOPIC, NOW, deviceA, "{\"name\":\"Waves\"}"))
        store.put(live("user:card", RecordType.CARD, NOW, deviceA, CARD_PAYLOAD))
        store.orphansToRepair = 1

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertTrue(report.succeeded)
        assertEquals(1, report.reparented)
        assertNotNull(store.recordOf("user:card", RecordType.CARD), "the card survives its topic")
    }

    @Test
    fun firstRunUploadOnlyIgnoresWhateverIsAlreadyThere() = runTest {
        val provider = InMemorySyncProvider()
        provider.seed(live("user:remote", RecordType.CARD, NOW, deviceB, CARD_PAYLOAD))
        val store = FakeStore(deviceA)
        store.put(live("user:local", RecordType.CARD, NOW, deviceA, "{\"front\":\"mine\"}"))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW })
            .sync(FirstRunPolicy.UPLOAD_LOCAL_ONLY)

        assertEquals(1, report.uploaded)
        assertEquals(0, report.downloaded)
        assertEquals(0, store.localRecords(RecordType.CARD).count { it.id.value == "user:remote" })
        assertEquals(2, provider.stored(RecordType.CARD).size)
    }

    @Test
    fun firstRunDownloadOnlyIgnoresLocalRecords() = runTest {
        val provider = InMemorySyncProvider()
        provider.seed(live("user:remote", RecordType.CARD, NOW, deviceB, CARD_PAYLOAD))
        val store = FakeStore(deviceA)
        store.put(live("user:local", RecordType.CARD, NOW, deviceA, "{\"front\":\"mine\"}"))

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW })
            .sync(FirstRunPolicy.DOWNLOAD_REMOTE_ONLY)

        assertEquals(0, report.uploaded)
        assertEquals(1, report.downloaded)
        assertEquals(CARD_PAYLOAD, store.payloadOf("user:remote", RecordType.CARD))
    }

    @Test
    fun aSuccessfulRunWritesAManifestDescribingThisBuild() = runTest {
        val provider = InMemorySyncProvider()
        val store = FakeStore(deviceA)

        val report = SyncEngine(provider, store, PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertTrue(report.succeeded)
        val manifest = provider.readManifest()
        assertNotNull(manifest)
        assertEquals(SyncLayout.SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals(PlainPayloadCodec.id, manifest.codecId)
        assertEquals(deviceA, manifest.deviceId)
        assertEquals(NOW, manifest.updatedAt)
    }

    @Test
    fun rateLimitingIsReportedAsSomethingToRetryNotACrash() = runTest {
        val provider = InMemorySyncProvider()
        provider.fault = { verb -> if (verb == "readManifest") SyncFailure.RateLimited(30_000) else null }

        val report = SyncEngine(provider, FakeStore(deviceA), PlainPayloadCodec, Clock.System, { NOW }).sync()

        assertFalse(report.succeeded)
        val failure = assertIs<SyncFailure.RateLimited>(report.failure)
        assertEquals(30_000L, failure.retryAfterMillis)
        assertTrue(failure.message.contains("30s"), failure.message)
    }

    @Test
    fun logSegmentsAreAppendOnlyAndStayInOrder() = runTest {
        val provider = InMemorySyncProvider()
        val segment = SyncLayout.segmentFor(2026, 10)

        provider.appendLogSegment(deviceA, segment, listOf("{\"r\":1}"))
        provider.appendLogSegment(deviceA, segment, listOf("{\"r\":2}", "{\"r\":3}"))
        provider.appendLogSegment(deviceB, segment, listOf("{\"other\":1}"))

        assertEquals(listOf("{\"r\":1}", "{\"r\":2}", "{\"r\":3}"), provider.readLogSegment(deviceA, segment))
        assertEquals(listOf(segment), provider.listLogSegments(deviceA))
        assertEquals(3, provider.appendCount)
        assertEquals(emptyList(), provider.readLogSegment(DeviceId("nobody"), segment))
    }

    @Test
    fun appendingNothingDoesNotCreateASegment() = runTest {
        val provider = InMemorySyncProvider()

        provider.appendLogSegment(deviceA, SyncLayout.segmentFor(2026, 1), emptyList())

        assertEquals(emptyList(), provider.listLogSegments(deviceA))
        assertEquals(0, provider.appendCount)
    }

    private companion object {
        const val NOW: Long = 1_760_000_000_000L
        const val DAY: Long = 24L * 60L * 60L * 1000L
        const val CARD_PAYLOAD: String = "{\"front\":\"What is a mole?\",\"back\":\"6.02e23\"}"

        /** Stand-in for the content hash; only equality matters to the engine. */
        fun hash(payload: String): String = payload.hashCode().toString(16)

        fun live(
            id: String,
            type: RecordType,
            updatedAt: Long,
            device: DeviceId,
            payload: String,
        ): SyncRecord = SyncRecord(
            id = RecordId(id),
            type = type,
            updatedAt = updatedAt,
            deviceId = device,
            payloadHash = hash(payload),
            deleted = false,
            payload = payload,
        )
    }

    /** The local half, as a map: same contract, no database. */
    private class FakeStore(override val deviceId: DeviceId) : SyncStore {
        private val records = mutableMapOf<RecordType, MutableMap<RecordId, SyncRecord>>()

        var downloadBatches: Int = 0
            private set
        var uploadBatches: Int = 0
            private set
        var uploadMarks: Int = 0
            private set
        var orphansToRepair: Int = 0

        fun put(record: SyncRecord) {
            records.getOrPut(record.type) { mutableMapOf() }[record.id] = record
        }

        fun recordOf(id: String, type: RecordType): SyncRecord? = records[type]?.get(RecordId(id))

        fun payloadOf(id: String, type: RecordType): String? = recordOf(id, type)?.payload

        override suspend fun localRecords(type: RecordType): List<SyncRecord> =
            records[type]?.values?.toList() ?: emptyList()

        override suspend fun applyDownloaded(newRecords: List<SyncRecord>) {
            downloadBatches++
            for (record in newRecords) {
                records.getOrPut(record.type) { mutableMapOf() }[record.id] = record
            }
        }

        override suspend fun markUploaded(newRecords: List<SyncRecord>) {
            uploadBatches++
            uploadMarks += newRecords.size
        }

        override suspend fun forgetTombstones(ids: List<RecordId>) {
            for (type in RecordType.entries) {
                for (id in ids) records[type]?.remove(id)
            }
        }

        override suspend fun repairOrphanedCards(): Int = orphansToRepair
    }
}
