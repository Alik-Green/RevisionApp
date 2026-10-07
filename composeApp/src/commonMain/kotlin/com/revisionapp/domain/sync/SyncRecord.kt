package com.revisionapp.domain.sync

/**
 * Identity of the device that last wrote a record.
 *
 * Generated once per install and stored locally. It exists so that two records
 * written in the same millisecond on two different devices still have a total
 * order, and so that per-device review-log segments can be attributed. See
 * docs/DECISIONS.md D41.
 */
@JvmInline
value class DeviceId(val value: String)

/**
 * Identity of a single syncable record.
 *
 * For most record types this is the id of the row it came from (`user:<uuid>`,
 * a topic id, a tag id). For the join and state types it is a composite, built
 * by [RecordId.of] so that the same pair of ids always produces the same key.
 */
@JvmInline
value class RecordId(val value: String) {
    companion object {
        /** Composite key for records that identify a relationship, not a row. */
        fun of(vararg parts: String): RecordId = RecordId(parts.joinToString(SEPARATOR))

        private const val SEPARATOR = "|"
    }
}

/** Name of one append-only review-log segment, e.g. `2026-10`. */
@JvmInline
value class LogSegmentName(val value: String)

/**
 * The kinds of user data that sync.
 *
 * Deliberately short. Content packs are *not* here: packs are fetched from the
 * content branch and must never be mixed with user data, in either direction.
 * Device settings are not here either -- a retention target or a theme choice
 * belongs to the device, not to the user's revision history. See D42.
 */
enum class RecordType(val wire: String) {
    CARD("card"),
    TOPIC("topic"),
    TAG("tag"),
    CARD_TAG("card-tag"),
    CARD_STATE("card-state"),
    LEARNED_ANSWER("learned-answer"),
    ;

    companion object {
        fun fromWire(wire: String): RecordType? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * The unit of synchronisation: one record.
 *
 * A record is self-describing and immutable once written -- the same id is
 * never edited in place by two devices in a way that requires a three-way
 * merge, because [updatedAt] plus [deviceId] give a total order (last write
 * wins). [payloadHash] lets a listing decide "do I already have this?" without
 * downloading the payload.
 *
 * [deleted] records are tombstones: they carry no payload and exist only to
 * outvote an older live copy arriving from another device. They are compacted
 * away after [TOMBSTONE_RETENTION_DAYS].
 */
data class SyncRecord(
    val id: RecordId,
    val type: RecordType,
    val updatedAt: Long,
    val deviceId: DeviceId,
    val payloadHash: String,
    val deleted: Boolean,
    val payload: String,
) {
    /** The header a remote listing exposes, without the payload. */
    val header: RemoteRecordHeader
        get() = RemoteRecordHeader(id, type, updatedAt, deviceId, payloadHash, deleted)

    companion object {
        /** How long a tombstone is kept before it may be compacted away. */
        const val TOMBSTONE_RETENTION_DAYS: Int = 90

        /** A tombstone carries no payload and no hash. */
        fun tombstone(
            id: RecordId,
            type: RecordType,
            updatedAt: Long,
            deviceId: DeviceId,
        ): SyncRecord = SyncRecord(
            id = id,
            type = type,
            updatedAt = updatedAt,
            deviceId = deviceId,
            payloadHash = "",
            deleted = true,
            payload = "",
        )
    }
}

/** Everything a listing needs to decide whether a download is required. */
data class RemoteRecordHeader(
    val id: RecordId,
    val type: RecordType,
    val updatedAt: Long,
    val deviceId: DeviceId,
    val payloadHash: String,
    val deleted: Boolean,
)

/**
 * The manifest at the root of a sync location.
 *
 * [schemaVersion] is how a build refuses to touch data written by a newer one:
 * reading a version above [SyncLayout.SCHEMA_VERSION] is a hard error rather
 * than a best-effort merge, because a newer schema may mean fields this build
 * would silently drop. [codecId] records how payloads were encoded, so a store
 * written encrypted is never read as plain text.
 */
data class SyncManifest(
    val schemaVersion: Int,
    val codecId: String,
    val deviceId: DeviceId,
    val updatedAt: Long,
)
