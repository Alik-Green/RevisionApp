package com.revisionapp.domain.sync

import com.revisionapp.data.sync.InMemorySyncProvider
import com.revisionapp.data.sync.MergedHistory
import com.revisionapp.data.sync.ReviewLogCodec
import com.revisionapp.data.sync.ReviewLogStore
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.srs.FsrsParameters
import com.revisionapp.domain.srs.FsrsScheduler
import com.revisionapp.domain.srs.Rating
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The append-only review log: merging segments from several devices, rebuilding
 * schedules from the merged history, and surviving lines this build cannot read.
 */
class ReviewLogTest {

    private val deviceA = DeviceId("dev-a")
    private val deviceB = DeviceId("dev-b")
    private val json = Json { ignoreUnknownKeys = true }
    private val codec = ReviewLogCodec(json)

    @Test
    fun reviewsFromTwoDevicesMergeIntoOneChronologicalHistory() = runTest {
        val provider = InMemorySyncProvider()
        val segment = SyncLayout.segmentFor(2026, 10)
        provider.appendLogSegment(deviceA, segment, listOf(codec.encode(review("e-a1", "user:1", 1_000, deviceA))))
        provider.appendLogSegment(deviceB, segment, listOf(codec.encode(review("e-b1", "user:1", 500, deviceB))))
        provider.appendLogSegment(deviceA, SyncLayout.segmentFor(2026, 9), listOf(codec.encode(review("e-a0", "user:1", 100, deviceA))))

        val history = ReviewLogStore(provider, codec).mergedHistory()

        assertEquals(3, history.reviews.size)
        assertEquals(listOf("e-a0", "e-b1", "e-a1"), history.reviews.map { it.eventId }, "sorted by time")
        assertEquals(2, history.devices)
        assertEquals(3, history.segments, "two months on one device, one on the other")
        assertEquals(0, history.duplicates)
        assertEquals(0, history.unreadable)
    }

    @Test
    fun theSameReviewArrivingTwiceIsCountedOnce() = runTest {
        // A segment re-uploaded, or two devices that both hold the same review.
        // Without deduplication a rebuild would double every interval.
        val provider = InMemorySyncProvider()
        val segment = SyncLayout.segmentFor(2026, 10)
        val line = codec.encode(review("e-shared", "user:1", 1_000, deviceA))
        provider.appendLogSegment(deviceA, segment, listOf(line))
        provider.appendLogSegment(deviceB, segment, listOf(line))

        val history = ReviewLogStore(provider, codec).mergedHistory()

        assertEquals(1, history.reviews.size)
        assertEquals(1, history.duplicates)
    }

    @Test
    fun mergingDoesNotDependOnTheOrderSegmentsWereReadIn() {
        // Two devices merging the same segments must produce the same history,
        // or they would rebuild different schedules from the same data.
        val first = listOf(review("e1", "user:1", 100, deviceA), review("e2", "user:1", 100, deviceB))
        val second = listOf(review("e3", "user:2", 300, deviceA))

        assertEquals(
            ReviewLogMerger.merge(listOf(first, second)),
            ReviewLogMerger.merge(listOf(second, first)),
        )
        assertEquals(
            ReviewLogMerger.merge(listOf(first, second)),
            ReviewLogMerger.merge(listOf(first.reversed(), second)),
        )
    }

    @Test
    fun aRebuiltScheduleReplaysEveryReview() {
        val scheduler = FsrsScheduler(FsrsParameters())
        val reviews = listOf(
            review("e1", "user:1", 1_000, deviceA),
            review("e2", "user:1", 2_000, deviceA),
            review("e3", "user:2", 3_000, deviceB),
        )

        val states = ScheduleRebuilder.rebuild(reviews, scheduler)

        assertEquals(2, states.size)
        assertEquals(2, states[CardId("user:1")]?.reps, "both reviews of the card were replayed")
        assertEquals(1, states[CardId("user:2")]?.reps)
        assertNotNull(states[CardId("user:1")]?.lastReviewAt)
    }

    @Test
    fun rebuildingIsDeterministicAndCarriesTheModeLadder() {
        val scheduler = FsrsScheduler(FsrsParameters())
        val reviews = listOf(
            review("e1", "user:1", 1_000, deviceA).copy(escalation = 2),
            review("e2", "user:1", 2_000, deviceA).copy(escalation = 3),
        )

        val first = ScheduleRebuilder.rebuild(reviews, scheduler)
        val second = ScheduleRebuilder.rebuild(reviews, scheduler)

        assertEquals(first, second)
        assertEquals(3, first[CardId("user:1")]?.modeEscalation, "the last logged escalation wins")
    }

    @Test
    fun twoDevicesRebuildTheSameScheduleFromTheSameHistory() {
        val scheduler = FsrsScheduler(FsrsParameters())
        val merged = ReviewLogMerger.merge(
            listOf(
                listOf(review("e1", "user:1", 1_000, deviceA)),
                listOf(review("e2", "user:1", 2_000, deviceB)),
            ),
        )

        assertEquals(
            ScheduleRebuilder.rebuild(merged, scheduler),
            ScheduleRebuilder.rebuild(merged.reversed().sortedBy { it.reviewedAt }, scheduler),
        )
    }

    @Test
    fun anUnreadableLineCostsOneReviewNotTheWholeSegment() = runTest {
        val provider = InMemorySyncProvider()
        val segment = SyncLayout.segmentFor(2026, 10)
        provider.appendLogSegment(
            deviceA,
            segment,
            listOf(
                codec.encode(review("good-1", "user:1", 1_000, deviceA)),
                "this is not json",
                "{\"eventId\":\"truncated\"",
                codec.encode(review("good-2", "user:1", 2_000, deviceA)),
            ),
        )

        val history = ReviewLogStore(provider, codec).mergedHistory()

        assertEquals(listOf("good-1", "good-2"), history.reviews.map { it.eventId })
        assertEquals(2, history.unreadable)
    }

    @Test
    fun aLineFromANewerBuildIsSkippedRatherThanRejectingTheHistory() = runTest {
        val provider = InMemorySyncProvider()
        val segment = SyncLayout.segmentFor(2026, 10)
        val futureMode = """
            {"eventId":"e-future","cardId":"user:9","mode":"SOME_MODE_ADDED_IN_2029",
             "rating":"${Rating.entries.first().name}","correct":true,"reviewedAt":1500,"deviceId":"dev-b"}
        """.trimIndent().replace("\n", "")
        provider.appendLogSegment(
            deviceA,
            segment,
            listOf(futureMode, codec.encode(review("e-known", "user:1", 1_000, deviceA))),
        )

        val history = ReviewLogStore(provider, codec).mergedHistory()

        assertEquals(listOf("e-known"), history.reviews.map { it.eventId })
        assertEquals(1, history.unreadable)
    }

    @Test
    fun anUnknownFieldFromANewerBuildIsIgnored() {
        val line = """
            {"eventId":"e1","cardId":"user:1","mode":"${StudyMode.entries.first().name}",
             "rating":"${Rating.entries.first().name}","correct":true,"reviewedAt":1500,
             "deviceId":"dev-a","someFieldAddedLater":42}
        """.trimIndent().replace("\n", "")

        val decoded = codec.decode(line)

        assertNotNull(decoded, "ignoreUnknownKeys must let older builds read newer lines")
        assertEquals("e1", decoded.eventId)
    }

    @Test
    fun blankLinesAreNotCountedAsUnreadable() = runTest {
        val provider = InMemorySyncProvider()
        provider.appendLogSegment(
            deviceA,
            SyncLayout.segmentFor(2026, 10),
            listOf("", "   ", codec.encode(review("e1", "user:1", 1_000, deviceA))),
        )

        val history = ReviewLogStore(provider, codec).mergedHistory()

        assertEquals(1, history.reviews.size)
        assertEquals(0, history.unreadable)
    }

    @Test
    fun aRefusalToAppendIsReportedNotThrown() = runTest {
        val provider = InMemorySyncProvider()
        provider.fault = { verb -> if (verb == "appendLogSegment") SyncFailure.Unreachable("offline") else null }
        val store = ReviewLogStore(provider, codec)

        val appended = store.append(deviceA, SyncLayout.segmentFor(2026, 10), listOf(review("e1", "user:1", 1_000, deviceA)))

        assertFalse(appended, "the review is still recorded locally; the next run retries")
    }

    @Test
    fun appendingNothingSucceeds() = runTest {
        val provider = InMemorySyncProvider()

        assertTrue(ReviewLogStore(provider, codec).append(deviceA, SyncLayout.segmentFor(2026, 10), emptyList()))
        assertEquals(emptyList(), provider.listLogDevices())
    }

    @Test
    fun anUnreachableProviderYieldsAnEmptyHistoryRatherThanACrash() = runTest {
        val provider = InMemorySyncProvider()
        provider.fault = { SyncFailure.Unreachable("no such folder") }

        val history = ReviewLogStore(provider, codec).mergedHistory()

        assertEquals(MergedHistory.EMPTY, history)
    }

    @Test
    fun eventIdsRoundTripThroughTheLineFormat() {
        val original = review("e1", "user:1", 1_760_000_000_000L, deviceA).copy(
            verdict = VerdictKind.entries.first(),
            correct = true,
            escalation = 2,
        )

        val decoded = codec.decode(codec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun aNullVerdictSurvivesTheRoundTrip() {
        val original = review("e1", "user:1", 1_000, deviceA).copy(verdict = null)

        val decoded = codec.decode(codec.encode(original))

        assertNotNull(decoded)
        assertNull(decoded.verdict)
    }

    @Test
    fun eventIdsAreDeterministicAndSeparateDevices() {
        val onA = LoggedReview.eventIdOf(deviceA, CardId("user:1"), 1_000, Rating.entries.first(), StudyMode.entries.first())
        val onB = LoggedReview.eventIdOf(deviceB, CardId("user:1"), 1_000, Rating.entries.first(), StudyMode.entries.first())
        val again = LoggedReview.eventIdOf(deviceA, CardId("user:1"), 1_000, Rating.entries.first(), StudyMode.entries.first())

        assertEquals(onA, again, "the same review must produce the same id")
        assertFalse(onA == onB, "two devices can review in the same millisecond")
    }

    @Test
    fun cardsNewToThisDeviceAreIdentified() {
        val scheduler = FsrsScheduler(FsrsParameters())
        val reviews = listOf(review("e1", "user:1", 1_000, deviceA), review("e2", "user:2", 2_000, deviceA))
        val existing = ScheduleRebuilder.rebuild(reviews.take(1), scheduler)

        assertEquals(listOf(CardId("user:2")), ScheduleRebuilder.cardsMissingFrom(reviews, existing))
        assertEquals(emptyList(), ScheduleRebuilder.cardsMissingFrom(reviews, ScheduleRebuilder.rebuild(reviews, scheduler)))
    }

    private fun review(
        eventId: String,
        cardId: String,
        reviewedAt: Long,
        device: DeviceId,
    ): LoggedReview = LoggedReview(
        eventId = eventId,
        cardId = CardId(cardId),
        mode = StudyMode.entries.first(),
        rating = Rating.entries.first(),
        verdict = null,
        correct = false,
        reviewedAt = reviewedAt,
        deviceId = device,
        escalation = 0,
    )
}
