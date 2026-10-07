package com.revisionapp.data.sync

import com.revisionapp.domain.sync.DeviceId
import com.revisionapp.domain.sync.LogSegmentName
import com.revisionapp.domain.sync.PayloadCodec
import com.revisionapp.domain.sync.ProviderCapabilities
import com.revisionapp.domain.sync.RecordId
import com.revisionapp.domain.sync.RecordType
import com.revisionapp.domain.sync.RemoteRecordHeader
import com.revisionapp.domain.sync.SyncException
import com.revisionapp.domain.sync.SyncFailure
import com.revisionapp.domain.sync.SyncLayout
import com.revisionapp.domain.sync.SyncManifest
import com.revisionapp.domain.sync.SyncProvider
import com.revisionapp.domain.sync.SyncRecord
import com.revisionapp.platform.SyncStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A [SyncProvider] over a folder the user chose.
 *
 * The transport that needs no account, no client id and no network: a directory
 * on disk, which may or may not be watched by a cloud desktop client. Pointing
 * it at a Google Drive, OneDrive, Dropbox or Syncthing folder gives cloud sync
 * without this app ever speaking to a cloud API -- the user's own sync client
 * does the moving. See D46.
 *
 * Two consequences of a folder having no API:
 *
 *  - listing headers means reading the files, because a filename carries no
 *    timestamp. That is the price of the transport, and it is why records are
 *    small and sharded rather than one big index.
 *  - a file can be listed before its bytes have arrived, or vanish between a
 *    listing and a read. Both are handled as "not there yet", never as a
 *    failure, and the next run sees the truth.
 */
class LocalFolderSyncProvider(
    private val storage: SyncStorage,
    private val json: Json,
    private val codec: PayloadCodec,
) : SyncProvider {

    private val records = RecordFileCodec(json, codec)

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        label = storage.label,
        supportsAtomicRename = storage.supportsAtomicRename,
        // Appending rewrites the file, and a cloud client may not have
        // materialised a write yet. Both are true of a folder and neither is
        // true of an object store, so they are reported rather than assumed.
        supportsAppend = false,
        stronglyConsistentListing = false,
        requiresNetwork = false,
    )

    override suspend fun readManifest(): SyncManifest? = guard("readManifest") {
        val bytes = storage.readBytes(SyncLayout.MANIFEST_PATH) ?: return@guard null
        runCatching { json.decodeFromString(SyncManifestDto.serializer(), bytes.decodeToString()) }
            .getOrNull()
            ?.let { it.toManifest() }
    }

    override suspend fun writeManifest(manifest: SyncManifest) = guard("writeManifest") {
        val dto = SyncManifestDto(
            schemaVersion = manifest.schemaVersion,
            codecId = manifest.codecId,
            deviceId = manifest.deviceId.value,
            updatedAt = manifest.updatedAt,
        )
        storage.writeBytes(
            SyncLayout.MANIFEST_PATH,
            json.encodeToString(SyncManifestDto.serializer(), dto).encodeToByteArray(),
        )
        Unit
    }

    override suspend fun listHeaders(type: RecordType): List<RemoteRecordHeader> = guard("listHeaders") {
        storage.list("records/${type.wire}/").mapNotNull { path -> recordAt(path)?.header }
    }

    override suspend fun getRecord(type: RecordType, id: RecordId): SyncRecord? = guard("getRecord") {
        recordAt(SyncLayout.recordPath(type, id))
    }

    override suspend fun putRecord(record: SyncRecord) = guard("putRecord") {
        storage.writeBytes(SyncLayout.recordPath(record.type, record.id), records.encode(record))
    }

    override suspend fun deleteRecord(type: RecordType, id: RecordId) = guard("deleteRecord") {
        storage.delete(SyncLayout.recordPath(type, id))
    }

    override suspend fun listLogDevices(): List<DeviceId> = guard("listLogDevices") {
        storage.list(LOG_PREFIX)
            .mapNotNull { it.removePrefix(LOG_PREFIX).substringBefore('/').takeIf(String::isNotEmpty) }
            .distinct()
            .map { DeviceId(SyncLayout.unescapeSegment(it)) }
            .sortedBy { it.value }
    }

    override suspend fun listLogSegments(deviceId: DeviceId): List<LogSegmentName> =
        guard("listLogSegments") {
            storage.list(devicePrefix(deviceId))
                .filter { it.endsWith(SEGMENT_SUFFIX) }
                .map {
                    LogSegmentName(
                        SyncLayout.unescapeSegment(
                            it.removePrefix(devicePrefix(deviceId)).removeSuffix(SEGMENT_SUFFIX),
                        ),
                    )
                }
                .sortedBy { it.value }
        }

    override suspend fun readLogSegment(deviceId: DeviceId, segment: LogSegmentName): List<String> =
        guard("readLogSegment") {
            val bytes = storage.readBytes(SyncLayout.logSegmentPath(deviceId, segment))
                ?: return@guard emptyList()
            bytes.decodeToString().lines().filter { it.isNotBlank() }
        }

    override suspend fun appendLogSegment(
        deviceId: DeviceId,
        segment: LogSegmentName,
        lines: List<String>,
    ) = guard("appendLogSegment") {
        if (lines.isEmpty()) return@guard
        val payload = lines.joinToString(separator = "\n", postfix = "\n").encodeToByteArray()
        storage.appendBytes(SyncLayout.logSegmentPath(deviceId, segment), payload)
    }

    /**
     * Reads one record file.
     *
     * A file that is missing, empty, half-written or from a newer build yields
     * null and is skipped: one unreadable record must not abort a sync, and on a
     * folder watched by a cloud client "listed but not readable yet" is normal.
     */
    private suspend fun recordAt(path: String): SyncRecord? {
        val bytes = runCatching { storage.readBytes(path) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        return records.decode(bytes)
    }

    private fun devicePrefix(deviceId: DeviceId): String =
        LOG_PREFIX + SyncLayout.escapeSegment(deviceId.value) + "/"

    /** Turns any transport throw into a [SyncException] the engine understands. */
    private suspend fun <T> guard(verb: String, block: suspend () -> T): T =
        runCatching { block() }.getOrElse { error ->
            if (error is SyncException) throw error
            throw SyncException(
                "$verb failed: ${error.message ?: error::class.simpleName}",
                SyncFailure.Storage(error.message ?: "The sync folder could not be used."),
                error,
            )
        }

    private companion object {
        const val LOG_PREFIX: String = "log/"
        const val SEGMENT_SUFFIX: String = ".jsonl"
    }
}

/** Wire shape of `manifest/sync.json`. */
@Serializable
data class SyncManifestDto(
    val schemaVersion: Int = SyncLayout.SCHEMA_VERSION,
    val codecId: String = "plain",
    val deviceId: String,
    val updatedAt: Long = 0L,
) {
    fun toManifest() = SyncManifest(
        schemaVersion = schemaVersion,
        codecId = codecId,
        deviceId = DeviceId(deviceId),
        updatedAt = updatedAt,
    )
}
