package com.revisionapp.domain.sync

/**
 * What a concrete provider can and cannot be relied on to do.
 *
 * The engine reads these flags rather than assuming: a shared folder watched by
 * a cloud desktop client gives no ordering guarantees and no atomic rename,
 * while an object store gives an atomic put but a listing that may lag. Both
 * are usable; neither is usable *blindly*.
 */
data class ProviderCapabilities(
    /** Short human-readable name, shown on the sync settings card. */
    val label: String,

    /** True if writing a temp file and renaming it is atomic on this backend. */
    val supportsAtomicRename: Boolean,

    /** True if bytes can be added to an existing object without rewriting it. */
    val supportsAppend: Boolean,

    /** True if a list immediately after a put always includes that put. */
    val stronglyConsistentListing: Boolean,

    /** True if syncing needs a network round trip (false for a local folder). */
    val requiresNetwork: Boolean,
)

/**
 * A transport for sync records.
 *
 * Four verbs -- list, get, put, delete -- plus the manifest and the append-only
 * review-log segments. Everything that has an opinion about *how* to merge is
 * in [SyncEngine]; a provider only moves bytes and reports what it sees. That
 * is what makes an in-memory fake a legitimate stand-in for a real backend in
 * tests, and what keeps a future WebDAV or S3 provider a thin adapter.
 *
 * Implementations throw [SyncException] on transport failure. They never return
 * a partial success silently: [putRecord] either durably stored the record or
 * it threw, which is what makes a crashed run safe to repeat.
 */
interface SyncProvider {
    val capabilities: ProviderCapabilities

    /** The manifest at the root, or null if the location has never been used. */
    suspend fun readManifest(): SyncManifest?

    suspend fun writeManifest(manifest: SyncManifest)

    /** Headers only -- enough to decide what needs downloading. */
    suspend fun listHeaders(type: RecordType): List<RemoteRecordHeader>

    /** The full record, or null if it is not there. */
    suspend fun getRecord(type: RecordType, id: RecordId): SyncRecord?

    suspend fun putRecord(record: SyncRecord)

    /**
     * Removes the object for [id] outright.
     *
     * Distinct from writing a tombstone: this is only ever called by tombstone
     * compaction, once a delete is old enough that no other device can still
     * hold a live copy of it.
     */
    suspend fun deleteRecord(type: RecordType, id: RecordId)

    suspend fun listLogSegments(deviceId: DeviceId): List<LogSegmentName>

    /** The lines of one segment, in the order they were appended. */
    suspend fun readLogSegment(deviceId: DeviceId, segment: LogSegmentName): List<String>

    /** Appends lines to one segment, creating it if necessary. */
    suspend fun appendLogSegment(deviceId: DeviceId, segment: LogSegmentName, lines: List<String>)
}

/**
 * Any failure to move bytes. Transport-level only: merge disagreements are
 * [SyncConflict], not exceptions, because they are an expected outcome of two
 * devices having been used independently.
 */
class SyncException(
    message: String,
    val failure: SyncFailure,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Why a sync attempt could not complete. Surfaced verbatim in the sync UI. */
sealed interface SyncFailure {
    val message: String

    /** The location could not be reached at all. */
    data class Unreachable(override val message: String) : SyncFailure

    /** No folder has been chosen yet. Not an error, just not configured. */
    data object NotConfigured : SyncFailure {
        override val message: String = "No sync location has been chosen yet."
    }

    /**
     * The remote data was written by a newer schema. Refusing is deliberate:
     * merging it would drop fields this build does not know about and then
     * re-upload the record as if that were the whole truth.
     */
    data class SchemaTooNew(val remoteVersion: Int, val localVersion: Int) : SyncFailure {
        override val message: String
            get() = "That sync location uses version $remoteVersion of the record format; " +
                "this build only understands up to $localVersion. Update the app before syncing."
    }

    /** The remote payloads were encoded with a codec this build cannot read. */
    data class CodecMismatch(val remoteCodec: String, val localCodec: String) : SyncFailure {
        override val message: String
            get() = "That sync location stores its records as '$remoteCodec' but this build " +
                "is configured for '$localCodec'."
    }

    /** The backend asked us to slow down. */
    data class RateLimited(val retryAfterMillis: Long) : SyncFailure {
        override val message: String
            get() = "The sync location is rate limiting requests. Try again in " +
                "${(retryAfterMillis + 999) / 1000}s."
    }

    /** Anything else: permissions, a full disk, a renamed file, a corrupt read. */
    data class Storage(override val message: String) : SyncFailure
}
