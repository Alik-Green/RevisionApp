package com.revisionapp.domain.srs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class FsrsTest {

    private val scheduler = FsrsScheduler()
    private val start: Instant = Instant.fromEpochMilliseconds(1_800_000_000_000L)

    @Test
    fun aNewCardIsDueImmediately() {
        val state = ScheduleState.new(start)
        assertTrue(state.isNew)
        assertTrue(state.isDue(start))
        assertEquals(0, state.reps)
        assertEquals(0, state.lapses)
    }

    @Test
    fun initialStabilityComesStraightFromTheWeights() {
        assertEquals(0.4872, scheduler.initialStability(Rating.AGAIN), 1e-9)
        assertEquals(1.4003, scheduler.initialStability(Rating.HARD), 1e-9)
        assertEquals(3.7145, scheduler.initialStability(Rating.GOOD), 1e-9)
        assertEquals(13.8206, scheduler.initialStability(Rating.EASY), 1e-9)
    }

    @Test
    fun theIntervalEqualsTheStabilityAtNinetyPercentRetention() {
        assertEquals(4.0, scheduler.intervalDays(4.0), 1e-6)
        assertEquals(1.0, scheduler.intervalDays(1.0), 1e-6)
        assertEquals(0.9, scheduler.retrievability(5.0, 5.0), 1e-6)
    }

    @Test
    fun retrievabilityFallsAsTimePasses() {
        val stability = 5.0
        val fresh = scheduler.retrievability(0.0, stability)
        val onTime = scheduler.retrievability(5.0, stability)
        val late = scheduler.retrievability(50.0, stability)

        assertTrue(fresh > onTime, "fresh=$fresh onTime=$onTime")
        assertTrue(onTime > late, "onTime=$onTime late=$late")
        assertTrue(late in 0.0..1.0)
    }

    @Test
    fun retrievabilityOfAnUnseenCardIsZero() {
        assertEquals(0.0, scheduler.retrievability(10.0, 0.0), 1e-9)
    }

    @Test
    fun easierRatingsProduceLongerIntervals() {
        val intervals = Rating.entries.map { scheduler.intervalDays(scheduler.initialStability(it)) }

        assertTrue(intervals.zipWithNext().all { (shorter, longer) -> longer > shorter }, "intervals=$intervals")
    }

    @Test
    fun aGoodReviewSchedulesTheCardDaysAhead() {
        val state = scheduler.review(ScheduleState.new(start), Rating.GOOD, start)

        assertFalse(state.isDue(start))
        assertTrue(state.isDue(state.dueAt))
        assertEquals(LearningState.REVIEW, state.state)
        assertEquals(1, state.reps)
        assertEquals(0, state.lapses)
        assertEquals(start, state.lastReviewAt)
        assertTrue(state.dueAt.toEpochMilliseconds() - start.toEpochMilliseconds() > MILLIS_PER_DAY)
    }

    @Test
    fun aLapseComesBackInsideTenMinutesAndCountsAsALapse() {
        val reviewed = scheduler.review(ScheduleState.new(start), Rating.GOOD, start)
        val lapsed = scheduler.review(reviewed, Rating.AGAIN, reviewed.dueAt)

        assertEquals(TEN_MINUTES_MILLIS, lapsed.dueAt.toEpochMilliseconds() - reviewed.dueAt.toEpochMilliseconds())
        assertEquals(LearningState.RELEARNING, lapsed.state)
        assertEquals(1, lapsed.lapses)
        assertEquals(2, lapsed.reps)
    }

    @Test
    fun theFirstLapseIsLearningNotRelearning() {
        val lapsed = scheduler.review(ScheduleState.new(start), Rating.AGAIN, start)
        assertEquals(LearningState.LEARNING, lapsed.state)
    }

    @Test
    fun forgettingNeverMakesAMemoryMoreDurable() {
        val reviewed = scheduler.review(ScheduleState.new(start), Rating.EASY, start)
        val lapsed = scheduler.review(reviewed, Rating.AGAIN, reviewed.dueAt)

        assertTrue(
            lapsed.stability <= reviewed.stability,
            "lapsed=${lapsed.stability} reviewed=${reviewed.stability}",
        )
        assertTrue(lapsed.stability > 0.0)
    }

    @Test
    fun repeatedGoodReviewsGrowTheInterval() {
        var state = ScheduleState.new(start)
        var now = start
        val stabilities = ArrayList<Double>()

        repeat(5) {
            state = scheduler.review(state, Rating.GOOD, now)
            stabilities.add(state.stability)
            now = state.dueAt
        }

        assertTrue(
            stabilities.zipWithNext().all { (earlier, later) -> later > earlier },
            "stabilities=$stabilities",
        )
    }

    @Test
    fun reviewingLateGrowsStabilityMoreThanReviewingEarly() {
        val first = scheduler.review(ScheduleState.new(start), Rating.GOOD, start)

        val onTime = scheduler.review(first, Rating.GOOD, first.dueAt)
        val early = scheduler.review(first, Rating.GOOD, start + 1.days)

        assertTrue(
            onTime.stability > early.stability,
            "onTime=${onTime.stability} early=${early.stability}",
        )
    }

    @Test
    fun difficultyStaysInsideItsRangeUnderAnyRatingPattern() {
        var state = ScheduleState.new(start)
        var now = start

        repeat(20) { index ->
            val rating = Rating.entries[index % Rating.entries.size]
            state = scheduler.review(state, rating, now)
            assertTrue(state.difficulty in MIN_DIFFICULTY..MAX_DIFFICULTY, "difficulty=${state.difficulty}")
            assertTrue(state.stability > 0.0)
            now = state.dueAt
        }
    }

    @Test
    fun failingRepeatedlyMakesACardHarder() {
        var state = ScheduleState.new(start)
        var now = start
        val first = scheduler.review(state, Rating.GOOD, now)

        state = first
        repeat(4) {
            now = state.dueAt
            state = scheduler.review(state, Rating.AGAIN, now)
        }

        assertTrue(state.difficulty > first.difficulty, "difficulty=${state.difficulty} first=${first.difficulty}")
    }

    @Test
    fun intervalsAreCappedAndNeverNegative() {
        assertTrue(scheduler.intervalDays(0.0) >= 0.0)
        assertTrue(scheduler.intervalDays(1e9) <= FsrsParameters.MAXIMUM_INTERVAL_DAYS)
    }

    @Test
    fun modeEscalationRisesOnSuccessAndResetsOnFailure() {
        assertEquals(0, scheduler.nextModeEscalation(0, Rating.AGAIN))
        assertEquals(0, scheduler.nextModeEscalation(3, Rating.AGAIN))
        assertEquals(2, scheduler.nextModeEscalation(2, Rating.HARD))
        assertEquals(1, scheduler.nextModeEscalation(0, Rating.GOOD))
        assertEquals(2, scheduler.nextModeEscalation(0, Rating.EASY))
        assertEquals(
            FsrsScheduler.MAX_MODE_ESCALATION,
            scheduler.nextModeEscalation(FsrsScheduler.MAX_MODE_ESCALATION, Rating.EASY),
        )
    }

    @Test
    fun theModeEscalationIsCarriedThroughAReview() {
        val easy = scheduler.review(ScheduleState.new(start), Rating.EASY, start)
        assertEquals(2, easy.modeEscalation)

        val lapsed = scheduler.review(easy, Rating.AGAIN, easy.dueAt)
        assertEquals(0, lapsed.modeEscalation)
    }

    @Test
    fun parametersAreValidated() {
        assertFailsWith<IllegalArgumentException> { FsrsParameters(weights = listOf(1.0)) }
        assertFailsWith<IllegalArgumentException> { FsrsParameters(desiredRetention = 0.5) }
        assertFailsWith<IllegalArgumentException> { FsrsParameters(desiredRetention = 0.99) }
        assertEquals(17, FsrsParameters.DEFAULT_WEIGHTS.size)
    }

    @Test
    fun aHigherDesiredRetentionShortensTheInterval() {
        val relaxed = FsrsScheduler(FsrsParameters(desiredRetention = 0.8))
        val strict = FsrsScheduler(FsrsParameters(desiredRetention = 0.95))

        assertTrue(relaxed.intervalDays(5.0) > strict.intervalDays(5.0))
    }

    @Test
    fun ratingsRoundTripThroughTheirWireValue() {
        for (rating in Rating.entries) {
            assertEquals(rating, Rating.fromValue(rating.value))
        }
        assertEquals(Rating.GOOD, Rating.fromValue(99))
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val TEN_MINUTES_MILLIS = 600_000L
        const val MIN_DIFFICULTY = 1.0
        const val MAX_DIFFICULTY = 10.0
    }
}
