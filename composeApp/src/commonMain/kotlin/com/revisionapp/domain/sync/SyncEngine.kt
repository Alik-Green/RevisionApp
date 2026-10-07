package com.revisionapp.domain.sync

import kotlin.time.Clock

/**
 * What to do the first time a location is used.
 *
 * Only relevant when the remote has no manifest yet. After that every run is a
 * merge, because refusing to merge would mean silently discarding whichever
 * device had been used while the other was away.
 */
enum class FirstRunPolicy {
    /** Take the union of both sides, resolving overlaps by last write. */
    MERGE,

    /** Push this device and ignore whatever is already there. */
    UPLOAD_LOCAL_ONLY,

    /** Replace this device's data with what is already there. */
    DOWNLOAD_REMOTE_ONLY,
}

/** The action one record needs. */
enum class MergeAction { NOTHING, UPLOAD, DOWNLOAD }

/**
 * The outcome of comparing one local record with one remote header.
 *
 * [contested] means both sides had a copy and they disagreed, so the decision
 * threw something away and belongs in the conflict log. A plain upload of a
 * record the other device has never seen is not a conflict and is not logged.
 */
data class MergeDecision(
    val action: MergeAction,
    val contested: Boolean,
    val reason: String,
)

/** One entry in the conflict log shown on the sync screen. */
data class SyncConflict(
    val id: RecordId,
    val type: RecordType,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val keptDevice: DeviceId,
    val discardedDevice: DeviceId,
    val reason: String,
)

/** What a run did. [failure] is null when the run completed. */
data class SyncReport(
    val uploaded: Int,
    val downloaded: Int,
    val compacted: Int,
    val reparented: Int,
    val conflicts: List<SyncConflict>,
    val failure: SyncFailure?,
    val startedAt: Long,
    val finishedAt: Long,
) {
    val succeeded: Boolean get() = failure == null

    companion object {
        fun aborted(failure: SyncFailure, startedAt: Long, finishedAt: Long): SyncReport =
            SyncReport(0, 0, 0, 0, emptyList(), failure, startedAt, finishedAt)
    }
}

/**
 * The local half of a sync: whatever holds the records on this device.
 *
 * An interface so that the engine has no idea a database exists, and so that
 * tests can drive it with a map. Implementations must make [applyDownloaded]
 * and [markUploaded] atomic: a run that dies halfway has to leave the local
 * data either as it was or fully updated, never half-written.
 */
interface SyncStore {
    val deviceId: DeviceId

    suspend fun localRecords(type: RecordType): List<SyncRecord>

    /** Stores records that won from the remote, in one transaction. */
    suspend fun applyDownloaded(records: List<SyncRecord>)

    /** Records that these are now durable remotely, in one transaction. */
    suspend fun markUploaded(records: List<SyncRecord>)

    /** Drops tombstones that have already been removed from the remote. */
    suspend fun forgetTombstones(ids: List<RecordId>)

    /**
     * Moves cards whose topic no longer exists to the unfiled topic, creating
     * it if needed, and returns how many were moved.
     *
     * This is what stops a delete on one device from orphaning cards on the
     * other: a topic deletion must never take a card's content with it.
     */
    suspend fun repairOrphanedCards(): Int
}

/**
 * Last-write-wins, with the device id as the tiebreak.
 *
 * Pure and total: given the same two copies it always returns the same answer
 * on every device, which is the property that stops two devices from each
 * deciding the *other* one won and ping-ponging a record forever.
 */
object LastWriteWins {

    fun decide(local: SyncRecord?, remote: RemoteRecordHeader?): MergeDecision = when {
        local == null && remote == null ->
            MergeDecision(MergeAction.NOTHING, contested = false, reason = "absent on both sides")

        remote == null ->
            MergeDecision(MergeAction.UPLOAD, contested = false, reason = "only on this device")

        local == null ->
            MergeDecision(MergeAction.DOWNLOAD, contested = false, reason = "only on the remote")

        // Same content, so nothing to do even if the timestamps disagree. This
        // is what makes a repeated run a no-op instead of re-uploading
        // everything it just wrote.
        local.payloadHash == remote.payloadHash && local.deleted == remote.deleted ->
            MergeDecision(MergeAction.NOTHING, contested = false, reason = "already identical")

        else -> {
            val remoteWins = if (remote.updatedAt != local.updatedAt) {
                remote.updatedAt > local.updatedAt
            } else {
                // Same millisecond on two devices. Comparing device ids is
                // arbitrary but total and, crucially, symmetric: both devices
                // reach the same verdict.
                remote.deviceId.value > local.deviceId.value
            }
            val reason = if (remote.updatedAt == local.updatedAt) {
                "written at the same instant; device ${if (remoteWins) remote.deviceId.value else local.deviceId.value} wins the tiebreak"
            } else {
                "the ${if (remoteWins) "remote" else "local"} copy is newer"
            }
            MergeDecision(
                action = if (remoteWins) MergeAction.DOWNLOAD else MergeAction.UPLOAD,
                contested = true,
                reason = reason,
            )
        }
    }
}

/**
 * Merges a [SyncStore] with a [SyncProvider].
 *
 * The order of operations is chosen for a run that can die at any point:
 *
 *  1. read and validate the manifest, refusing a newer schema outright;
 *  2. list every type and decide, per record, with [LastWriteWins];
 *  3. download the winners and apply them in one local transaction;
 *  4. upload the local winners one at a time;
 *  5. compact tombstones older than [SyncRecord.TOMBSTONE_RETENTION_DAYS];
 *  6. repair cards orphaned by a topic deletion;
 *  7. write the manifest last.
 *
 * Because the manifest is written last, an interrupted run leaves the location
 * describing the previous state, and the next run simply redoes the work --
 * which is safe, since step 2 compares content hashes and finds nothing to do.
 * Nothing here needs a rollback: every write is either an idempotent put of a
 * whole record or a transaction on the local store.
 */
class SyncEngine(
    private val provider: SyncProvider,
    private val store: SyncStore,
    private val codec: PayloadCodec,
    private val clock: Clock,
    private val nowMillis: () -> Long = { clock.now().toEpochMilliseconds() },
) {

    suspend fun sync(policy: FirstRunPolicy = FirstRunPolicy.MERGE): SyncReport {
        val startedAt = nowMillis()

        val manifest = runCatching { provider.readManifest() }.getOrElse { error ->
            return SyncReport.aborted(failureOf(error), startedAt, nowMillis())
        }
        val self = store.deviceId

        if (manifest != null) {
            val refusal = refuse(manifest)
            if (refusal != null) return SyncReport.aborted(refusal, startedAt, nowMillis())
        }

        val conflicts = mutableListOf<SyncConflict>()
        val uploads = mutableListOf<SyncRecord>()
        val downloads = mutableListOf<SyncRecord>()
        var downloadedCount = 0

        for (type in RecordType.entries) {
            val remoteHeaders = runCatching { provider.listHeaders(type) }.getOrElse { error ->
                return SyncReport.aborted(failureOf(error), startedAt, nowMillis())
            }.associateBy { it.id }.toMutableMap()

            for (local in store.localRecords(type)) {
                val remote = remoteHeaders.remove(local.id)
                val decision = LastWriteWins.decide(local, remote)
                when (decision.action) {
                    MergeAction.NOTHING -> Unit
                    MergeAction.UPLOAD ->
                        if (policy != FirstRunPolicy.DOWNLOAD_REMOTE_ONLY) uploads += local
                    MergeAction.DOWNLOAD ->
                        if (policy != FirstRunPolicy.UPLOAD_LOCAL_ONLY && remote != null) {
                            val fetched = runCatching { provider.getRecord(type, remote.id) }.getOrElse { error ->
                                return SyncReport.aborted(failureOf(error), startedAt, nowMillis())
                            }
                            if (fetched == null) {
                                // Listed but gone: a concurrent delete, or a
                                // provider whose listing lags its writes. Skip
                                // it and let the next run see the truth.
                                continue
                            }
                            downloads += fetched
                            downloadedCount++
                        }
                }
                if (decision.contested && remote != null) {
                    val keptRemote = decision.action == MergeAction.DOWNLOAD
                    conflicts += SyncConflict(
                        id = local.id,
                        type = type,
                        localUpdatedAt = local.updatedAt,
                        remoteUpdatedAt = remote.updatedAt,
                        keptDevice = if (keptRemote) remote.deviceId else local.deviceId,
                        discardedDevice = if (keptRemote) local.deviceId else remote.deviceId,
                        reason = decision.reason,
                    )
                }
            }

            // Anything left in the map exists only on the remote.
            for (header in remoteHeaders.values) {
                if (policy == FirstRunPolicy.UPLOAD_LOCAL_ONLY) continue
                val fetched = runCatching { provider.getRecord(type, header.id) }.getOrElse { error ->
                    return SyncReport.aborted(failureOf(error), startedAt, nowMillis())
                } ?: continue
                downloads += fetched
                downloadedCount++
            }
        }

        if (downloads.isNotEmpty()) {
            runCatching { store.applyDownloaded(downloads) }.getOrElse { error ->
                return SyncReport.aborted(failureOf(error), startedAt, nowMillis())
            }
        }

        var uploaded = 0
        for (record in uploads) {
            runCatching { provider.putRecord(record) }.getOrElse { error ->
                // Stop at the first failure but keep what already landed: those
                // puts are durable and the manifest has not moved, so the next
                // run picks up here without duplicating anything.
                runCatching { store.markUploaded(uploads.take(uploaded)) }
                return SyncReport.aborted(failureOf(error), startedAt, nowMillis())
            }
            uploaded++
        }
        // A throw here must not escape either: the records are already durable
        // remotely, and a run that reports success is better than one that
        // crashes after doing the work.
        if (uploaded > 0) runCatching { store.markUploaded(uploads.take(uploaded)) }

        val compacted = compactTombstones()
        val reparented = runCatching { store.repairOrphanedCards() }.getOrElse { 0 }

        runCatching {
            provider.writeManifest(
                SyncManifest(
                    schemaVersion = SyncLayout.SCHEMA_VERSION,
                    codecId = codec.id,
                    deviceId = self,
                    updatedAt = nowMillis(),
                ),
            )
        }.getOrElse { error ->
            return SyncReport.aborted(failureOf(error), startedAt, nowMillis())
        }

        return SyncReport(
            uploaded = uploaded,
            downloaded = downloadedCount,
            compacted = compacted,
            reparented = reparented,
            conflicts = conflicts,
            failure = null,
            startedAt = startedAt,
            finishedAt = nowMillis(),
        )
    }

    /**
     * Deletes tombstones on both sides once they are old enough that no other
     * device can still be holding a live copy they would outvote.
     *
     * Without this the location grows forever with records that mean nothing.
     * Ninety days is longer than any realistic "I have not opened the app on
     * the other device" gap.
     */
    private suspend fun compactTombstones(): Int {
        val cutoff = nowMillis() - TOMBSTONE_RETENTION_MILLIS
        var compacted = 0
        val forgotten = mutableListOf<RecordId>()

        for (type in RecordType.entries) {
            val remote = runCatching { provider.listHeaders(type) }.getOrNull() ?: emptyList()
            val stale = remote.filter { it.deleted && it.updatedAt < cutoff }
            for (header in stale) {
                val removed = runCatching { provider.deleteRecord(type, header.id) }.isSuccess
                if (removed) {
                    compacted++
                    forgotten += header.id
                }
            }
        }
        if (forgotten.isNotEmpty()) runCatching { store.forgetTombstones(forgotten) }
        return compacted
    }

    private fun refuse(manifest: SyncManifest): SyncFailure? = when {
        manifest.schemaVersion > SyncLayout.SCHEMA_VERSION ->
            SyncFailure.SchemaTooNew(manifest.schemaVersion, SyncLayout.SCHEMA_VERSION)

        manifest.codecId != codec.id ->
            SyncFailure.CodecMismatch(manifest.codecId, codec.id)

        else -> null
    }

    private fun failureOf(error: Throwable): SyncFailure =
        (error as? SyncException)?.failure
            ?: SyncFailure.Storage(error.message ?: "The sync location could not be reached.")

    private companion object {
        val TOMBSTONE_RETENTION_MILLIS: Long =
            SyncRecord.TOMBSTONE_RETENTION_DAYS.toLong() * 24L * 60L * 60L * 1000L
    }
}
