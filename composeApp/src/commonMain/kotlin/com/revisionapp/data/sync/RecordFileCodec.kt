package com.revisionapp.data.sync

import com.revisionapp.domain.sync.DeviceId
import com.revisionapp.domain.sync.PayloadCodec
import com.revisionapp.domain.sync.RecordId
import com.revisionapp.domain.sync.RecordType
import com.revisionapp.domain.sync.SyncRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The on-disk shape of one record.
 *
 * Every field has a default so that a file written by a build with fewer fields
 * still reads, and `type` is a string resolved by hand so that a type this
 * build has never heard of is skipped rather than failing the whole listing.
 */
@Serializable
data class RecordFile(
    val id: String,
    val type: String,
    val updatedAt: Long = 0L,
    val deviceId: String,
    val payloadHash: String = "",
    val deleted: Boolean = false,
    val payload: String = "",
)

/** Translates between [SyncRecord] and the bytes in a folder. */
class RecordFileCodec(
    private val json: Json,
    private val codec: PayloadCodec,
) {

    fun encode(record: SyncRecord): ByteArray = json
        .encodeToString(
            RecordFile.serializer(),
            RecordFile(
                id = record.id.value,
                type = record.type.wire,
                updatedAt = record.updatedAt,
                deviceId = record.deviceId.value,
                payloadHash = record.payloadHash,
                deleted = record.deleted,
                payload = codec.encode(record.payload),
            ),
        )
        .encodeToByteArray()

    /** Null when the file cannot be understood. Never throws. */
    fun decode(bytes: ByteArray): SyncRecord? {
        val type = runCatching {
            val file = json.decodeFromString(RecordFile.serializer(), bytes.decodeToString())
            val resolved = RecordType.fromWire(file.type) ?: return@runCatching null
            SyncRecord(
                id = RecordId(file.id),
                type = resolved,
                updatedAt = file.updatedAt,
                deviceId = DeviceId(file.deviceId),
                payloadHash = file.payloadHash,
                deleted = file.deleted,
                payload = if (file.deleted) "" else codec.decode(file.payload),
            )
        }.getOrNull()
        return type
    }
}
