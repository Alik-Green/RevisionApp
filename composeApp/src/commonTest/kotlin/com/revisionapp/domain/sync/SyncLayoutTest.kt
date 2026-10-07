package com.revisionapp.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncLayoutTest {

    @Test
    fun idsRoundTripThroughEscaping() {
        val ids = listOf(
            "user:2f1c9a7e-6b40-4c11-9d2e-8a5f0b3c7d11",
            "builtin:ocr-a-level-physics-h556:quantum:0012",
            "card-state|user:9d02|2026-10-07",
            "_leading-underscore",
            "trailing-underscore_",
            "dot.in.the.middle",
            "unicode-θ-ν-π",
            "a/b\\c",
            "percent%25literal",
        )
        for (id in ids) {
            assertEquals(id, SyncLayout.unescapeSegment(SyncLayout.escapeSegment(id)), "round trip of $id")
        }
    }

    @Test
    fun escapedSegmentsAreSafeAsFilenames() {
        // ':' is illegal in a Windows filename and '/' would add a path level.
        // User ids contain both (a 'user:' prefix, and '|' in composite keys),
        // so escaping is not cosmetic -- it is what makes the desktop provider
        // work on Windows at all.
        for (id in listOf("user:abc", "a|b", "a/b", "a\\b", "a:b:c", "..", ".")) {
            val escaped = SyncLayout.escapeSegment(id)
            assertFalse(escaped.contains(':'), "colon survived in $escaped")
            assertFalse(escaped.contains('/'), "slash survived in $escaped")
            assertFalse(escaped.contains('\\'), "backslash survived in $escaped")
        }
    }

    @Test
    fun reservedSegmentsRoundTripInsteadOfClimbingOutOfTheShard() {
        assertEquals(".", SyncLayout.unescapeSegment(SyncLayout.escapeSegment(".")))
        assertEquals("..", SyncLayout.unescapeSegment(SyncLayout.escapeSegment("..")))
        assertNotEquals(".", SyncLayout.escapeSegment("."))
        assertNotEquals("..", SyncLayout.escapeSegment(".."))
    }

    @Test
    fun aMalformedEscapeIsPassedThroughRatherThanThrowing() {
        // A listing can contain a file this build did not write. One odd name
        // must not abort the whole sync.
        assertEquals("100% sure", SyncLayout.unescapeSegment("100% sure"))
        assertEquals("truncated%2", SyncLayout.unescapeSegment("truncated%2"))
    }

    @Test
    fun recordPathsFollowTheDocumentedLayout() {
        val id = RecordId("user:abc123")
        val path = SyncLayout.recordPath(RecordType.CARD, id)

        assertEquals("records/card/${SyncLayout.shardOf(id)}/${SyncLayout.escapeSegment(id.value)}.json", path)
        assertEquals(4, path.split('/').size, "path was $path")
        assertTrue(path.endsWith(".json"))
    }

    @Test
    fun shardsAreDerivedFromTheIdSoEveryDeviceAgrees() {
        val id = RecordId("user:abc123")

        assertEquals(2, SyncLayout.shardOf(id).length)
        assertEquals(SyncLayout.shardOf(id), SyncLayout.shardOf(id), "shard must be stable")
        assertEquals(SyncLayout.escapeSegment(id.value).take(2), SyncLayout.shardOf(id))
    }

    @Test
    fun logSegmentsArePerDevicePerMonth() {
        val device = DeviceId("7f3ab21c")
        val segment = SyncLayout.segmentFor(2026, 10)

        assertEquals("2026-10", segment.value)
        assertEquals("log/7f3ab21c/2026-10.jsonl", SyncLayout.logSegmentPath(device, segment))
    }

    @Test
    fun segmentNamesAreZeroPaddedSoTheySortChronologically() {
        assertEquals("2026-01", SyncLayout.segmentFor(2026, 1).value)
        assertEquals("0999-12", SyncLayout.segmentFor(999, 12).value)

        val names = listOf(
            SyncLayout.segmentFor(2026, 10),
            SyncLayout.segmentFor(2026, 2),
            SyncLayout.segmentFor(2025, 11),
        ).map { it.value }.sorted()
        assertEquals(listOf("2025-11", "2026-02", "2026-10"), names)
    }

    @Test
    fun everyRecordTypeHasADistinctWireNameThatResolvesBack() {
        val wires = RecordType.entries.map { it.wire }

        assertEquals(RecordType.entries.size, wires.toSet().size, "wire names must be distinct")
        for (type in RecordType.entries) {
            assertEquals(type, RecordType.fromWire(type.wire))
        }
        assertNull(RecordType.fromWire("card_state"))
        assertNull(RecordType.fromWire(""))
    }

    @Test
    fun contentPacksAndDeviceSettingsAreNotSyncable() {
        // The one rule that must never be broken by a future record type: packs
        // come from the content branch and device settings belong to the device.
        val wires = RecordType.entries.map { it.wire }

        assertFalse(wires.any { it.contains("pack") })
        assertFalse(wires.any { it.contains("setting") })
    }

    @Test
    fun aTombstoneCarriesNoPayload() {
        val tombstone = SyncRecord.tombstone(
            id = RecordId("user:gone"),
            type = RecordType.CARD,
            updatedAt = 1_700_000_000_000L,
            deviceId = DeviceId("dev-a"),
        )

        assertTrue(tombstone.deleted)
        assertEquals("", tombstone.payload)
        assertEquals("", tombstone.payloadHash)
    }

    @Test
    fun aHeaderCarriesTheSameIdentityAsItsRecord() {
        val record = SyncRecord(
            id = RecordId("user:x"),
            type = RecordType.TOPIC,
            updatedAt = 42L,
            deviceId = DeviceId("dev-b"),
            payloadHash = "abc",
            deleted = false,
            payload = "{\"name\":\"Mechanics\"}",
        )

        assertEquals(record.id, record.header.id)
        assertEquals(record.type, record.header.type)
        assertEquals(record.updatedAt, record.header.updatedAt)
        assertEquals(record.deviceId, record.header.deviceId)
        assertEquals(record.payloadHash, record.header.payloadHash)
        assertEquals(record.deleted, record.header.deleted)
    }

    @Test
    fun compositeRecordIdsAreDeterministic() {
        assertEquals(RecordId.of("a", "b"), RecordId.of("a", "b"))
        assertTrue(RecordId.of("a", "b").value.contains("a"))
        assertTrue(RecordId.of("a", "b").value.contains("b"))
    }

    @Test
    fun thePlainCodecIsTheIdentityAndNamesItself() {
        assertEquals("plain", PlainPayloadCodec.id)
        assertEquals(PlainPayloadCodec.id, PlainPayloadCodec.CODEC_ID)
        assertEquals("{\"a\":1}", PlainPayloadCodec.encode("{\"a\":1}"))
        assertEquals("{\"a\":1}", PlainPayloadCodec.decode("{\"a\":1}"))
        assertEquals("", PlainPayloadCodec.encode(""))
    }
}
