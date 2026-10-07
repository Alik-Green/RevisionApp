package com.revisionapp.data.sync

import com.revisionapp.domain.sync.DeviceId
import com.revisionapp.domain.sync.LogSegmentName
import com.revisionapp.domain.sync.ProviderCapabilities
import com.revisionapp.domain.sync.RecordId
import com.revisionapp.domain.sync.RecordType
import com.revisionapp.domain.sync.RemoteRecordHeader
import com.revisionapp.domain.sync.SyncException
import com.revisionapp.domain.sync.SyncFailure
import com.revisionapp.domain.sync.SyncManifest
import com.revisionapp.domain.sync.SyncProvider
import com.revisionapp.domain.sync.SyncRecord

/**
 * An in-memory [SyncProvider], and the reason the sync engine can be tested
 * properly rather than optimistically.
 *
 * It is a real implementation of the same interface a folder or a WebDAV
 * server would satisfy -- not a mock that has been told what to answer -- so
 * the engine's decisions are exercised against something that behaves like a
 * backend. It also carries the two things a real backend does and a stub
 * usually forgets:
 *
 *  - [fault] makes any verb fail on demand, so crash-safety and partial-failure
 *    handling are tested rather than assumed;
 *  - [hiddenIds] makes a record appear in a listing but vanish when fetched,
 *    which is what an eventually consistent store, or a concurrent delete on
 *    another device, actually looks like.
 *
 * Injectable anywhere a [SyncProvider] is wanted, including as the default in a
 * build with no location configured, so that the sync screen always has
 * something honest to talk to.
 */
class InMemorySyncProvider(
    override val capabilities: ProviderCapabilities = CAPABILITIES,
) : SyncProvider {

    private val records = mutableMapOf<RecordType, MutableMap<RecordId, SyncRecord>>()
    private val segments = mutableMapOf<String, MutableList<String>>()
    private var manifest: SyncManifest? = null

    /**
     * Fault injection. Called before every verb with that verb's name; return a
     * failure to make the call throw [SyncException].
     */
    var fault: ((verb: String) -> SyncFailure?)? = null

    /**
     * Ids that are listed but not fetchable, modelling a store whose listing
     * has not caught up with a delete.
     */
    val hiddenIds: MutableSet<RecordId> = mutableSetOf()

    /** Every verb called, in order. Lets a test assert what a run touched. */
    val callLog: MutableList<String> = mutableListOf()

    var putCount: Int = 0
        private set

    var deleteCount: Int = 0
        private set

    var appendCount: Int = 0
        private set

    /** Everything currently stored, for assertions. */
    fun stored(type: RecordType): List<SyncRecord> = records[type]?.values?.toList() ?: emptyList()

    fun storedCount(): Int = records.values.sumOf { it.size }

    fun clearCallLog() {
        callLog.clear()
    }

    /** Seeds a record without going through the provider verbs. */
    fun seed(record: SyncRecord) {
        records.getOrPut(record.type) { mutableMapOf() }[record.id] = record
    }

    private fun guard(verb: String) {
        callLog += verb
        val failure = fault?.invoke(verb)
        if (failure != null) throw SyncException(failure.message, failure)
    }

    override suspend fun readManifest(): SyncManifest? {
        guard("readManifest")
        return manifest
    }

    override suspend fun writeManifest(manifest: SyncManifest) {
        guard("writeManifest")
        this.manifest = manifest
    }

    override suspend fun listHeaders(type: RecordType): List<RemoteRecordHeader> {
        guard("listHeaders")
        return records[type]?.values?.map { it.header } ?: emptyList()
    }

    override suspend fun getRecord(type: RecordType, id: RecordId): SyncRecord? {
        guard("getRecord")
        if (id in hiddenIds) return null
        return records[type]?.get(id)
    }

    override suspend fun putRecord(record: SyncRecord) {
        guard("putRecord")
        records.getOrPut(record.type) { mutableMapOf() }[record.id] = record
        putCount++
    }

    override suspend fun deleteRecord(type: RecordType, id: RecordId) {
        guard("deleteRecord")
        records[type]?.remove(id)
        deleteCount++
    }

    override suspend fun listLogSegments(deviceId: DeviceId): List<LogSegmentName> {
        guard("listLogSegments")
        val prefix = segmentPrefix(deviceId)
        return segments.keys
            .filter { it.startsWith(prefix) }
            .map { LogSegmentName(it.removePrefix(prefix)) }
            .sortedBy { it.value }
    }

    override suspend fun readLogSegment(deviceId: DeviceId, segment: LogSegmentName): List<String> {
        guard("readLogSegment")
        return segments[segmentKey(deviceId, segment)]?.toList() ?: emptyList()
    }

    override suspend fun appendLogSegment(
        deviceId: DeviceId,
        segment: LogSegmentName,
        lines: List<String>,
    ) {
        guard("appendLogSegment")
        if (lines.isEmpty()) return
        segments.getOrPut(segmentKey(deviceId, segment)) { mutableListOf() }.addAll(lines)
        appendCount++
    }

    private fun segmentPrefix(deviceId: DeviceId): String = deviceId.value + "/"

    private fun segmentKey(deviceId: DeviceId, segment: LogSegmentName): String =
        segmentPrefix(deviceId) + segment.value

    companion object {
        val CAPABILITIES: ProviderCapabilities = ProviderCapabilities(
            label = "In memory",
            supportsAtomicRename = true,
            supportsAppend = true,
            stronglyConsistentListing = true,
            requiresNetwork = false,
        )
    }
}
