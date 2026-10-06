package com.revisionapp.domain.srs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant

/** The four buttons a flashcard review offers, in increasing order of reward. */
enum class Rating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
    ;

    companion object {
        fun fromValue(value: Int): Rating = entries.firstOrNull { it.value == value } ?: GOOD
    }
}

/** Where a card is in its learning life. */
enum class LearningState {
    NEW,
    LEARNING,
    RELEARNING,
    REVIEW,
}

/**
 * Everything the scheduler needs to know about one card. Persisted in the
 * `card_state` table, which a content sync never touches — see
 * docs/DECISIONS.md D15.
 */
data class ScheduleState(
    val stability: Double,
    val difficulty: Double,
    val dueAt: Instant,
    val lastReviewAt: Instant?,
    val reps: Int,
    val lapses: Int,
    val state: LearningState,
    /**
     * How far this card has climbed the easy-to-hard mode ladder. Mixed mode uses
     * it to stop asking questions the user has already answered correctly in a
     * weaker mode.
     */
    val modeEscalation: Int,
) {
    val isNew: Boolean get() = state == LearningState.NEW

    fun isDue(now: Instant): Boolean = !dueAt.isAfter(now)

    companion object {
        /** A brand-new card is due immediately. */
        fun new(now: Instant): ScheduleState = ScheduleState(
            stability = 0.0,
            difficulty = 0.0,
            dueAt = now,
            lastReviewAt = null,
            reps = 0,
            lapses = 0,
            state = LearningState.NEW,
            modeEscalation = 0,
        )
    }
}

/**
 * FSRS-4.5 tuning weights. The defaults are the published ones; they are kept in
 * a value type so that parameters fitted to a user's own review history can be
 * dropped in later without touching the scheduler.
 */
data class FsrsParameters(
    val weights: List<Double> = DEFAULT_WEIGHTS,
    val desiredRetention: Double = DEFAULT_RETENTION,
    val maximumIntervalDays: Double = MAXIMUM_INTERVAL_DAYS,
) {
    init {
        require(weights.size == WEIGHT_COUNT) {
            "FSRS-4.5 needs exactly $WEIGHT_COUNT weights but got ${weights.size}"
        }
        require(desiredRetention in MINIMUM_RETENTION..MAXIMUM_RETENTION) {
            "desired retention must be in $MINIMUM_RETENTION..$MAXIMUM_RETENTION"
        }
    }

    operator fun get(index: Int): Double = weights[index]

    companion object {
        const val WEIGHT_COUNT: Int = 17
        const val DEFAULT_RETENTION: Double = 0.9
        const val MINIMUM_RETENTION: Double = 0.7
        const val MAXIMUM_RETENTION: Double = 0.97
        const val MAXIMUM_INTERVAL_DAYS: Double = 36_500.0

        /** FSRS-4.5 forgetting curve: `R = (1 + FACTOR * t / S) ^ DECAY`. */
        const val DECAY: Double = -0.5
        const val FACTOR: Double = 19.0 / 81.0

        val DEFAULT_WEIGHTS: List<Double> = listOf(
            0.4872, 1.4003, 3.7145, 13.8206,
            5.1618, 1.2298, 0.8975, 0.031,
            1.6474, 0.1367, 1.0461, 2.1072,
            0.0793, 0.3246, 1.587, 0.2272, 2.8755,
        )
    }
}

/**
 * FSRS-4.5 scheduling. Every study mode feeds this one scheduler, so a card
 * reviewed by typing an answer and a card reviewed by flipping a flashcard share
 * a single memory model.
 *
 * Pure and deterministic: `now` is always passed in, so tests never depend on the
 * wall clock.
 */
class FsrsScheduler(private val parameters: FsrsParameters = FsrsParameters()) {

    /** Applies one review and returns the new schedule for the card. */
    fun review(state: ScheduleState, rating: Rating, now: Instant): ScheduleState {
        val elapsedDays = elapsedDaysSince(state.lastReviewAt, now)

        val stability: Double
        val difficulty: Double
        if (state.reps == 0) {
            stability = initialStability(rating)
            difficulty = initialDifficulty(rating)
        } else {
            difficulty = nextDifficulty(state.difficulty, rating)
            val retrievability = retrievability(elapsedDays, state.stability)
            stability = if (rating == Rating.AGAIN) {
                nextForgetStability(state.difficulty, state.stability, retrievability)
            } else {
                nextRecallStability(state.difficulty, state.stability, retrievability, rating)
            }
        }

        val dueAt = if (rating == Rating.AGAIN) {
            now + RELEARNING_DELAY
        } else {
            now + intervalDays(stability).toLong().coerceAtLeast(1L).days
        }

        return ScheduleState(
            stability = stability,
            difficulty = difficulty,
            dueAt = dueAt,
            lastReviewAt = now,
            reps = state.reps + 1,
            lapses = state.lapses + if (rating == Rating.AGAIN) 1 else 0,
            state = when (rating) {
                Rating.AGAIN -> if (state.reps == 0) LearningState.LEARNING else LearningState.RELEARNING
                else -> LearningState.REVIEW
            },
            modeEscalation = nextModeEscalation(state.modeEscalation, rating),
        )
    }

    /** `S_0(G) = w[G-1]`: how long the memory lasts after the very first rating. */
    fun initialStability(rating: Rating): Double = parameters[rating.value - 1]

    /** `D_0(G) = w4 - e^(w5 * (G-1)) + 1`, clamped to 1..10. */
    fun initialDifficulty(rating: Rating): Double =
        constrainDifficulty(parameters[4] - exp(parameters[5] * (rating.value - 1)) + 1.0)

    /**
     * Difficulty moves against the rating and then mean-reverts towards `w4`,
     * which is what stops FSRS from falling into "ease hell".
     */
    fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = difficulty - parameters[6] * (rating.value - 3)
        val meanReverted = parameters[7] * parameters[4] + (1.0 - parameters[7]) * delta
        return constrainDifficulty(meanReverted)
    }

    /** Probability of recall [elapsedDays] after the last review. */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val days = max(0.0, elapsedDays)
        return (1.0 + FsrsParameters.FACTOR * days / stability).pow(FsrsParameters.DECAY)
    }

    /** Stability after a successful recall (Hard, Good or Easy). */
    fun nextRecallStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) parameters[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) parameters[16] else 1.0
        val growth = exp(parameters[8]) *
            (11.0 - difficulty) *
            stability.pow(-parameters[9]) *
            (exp((1.0 - retrievability) * parameters[10]) - 1.0) *
            hardPenalty *
            easyBonus
        return max(stability, stability * (1.0 + growth))
    }

    /**
     * Stability after a lapse. Clamped to at most the previous stability: failing
     * a card must never make it more durable.
     */
    fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val raw = parameters[11] *
            difficulty.pow(-parameters[12]) *
            ((stability + 1.0).pow(parameters[13]) - 1.0) *
            exp((1.0 - retrievability) * parameters[14])
        return min(raw, stability)
    }

    /** Solves `R(t,S) = desiredRetention` for `t`; equals `S` at 90% retention. */
    fun intervalDays(stability: Double): Double {
        val retention = parameters.desiredRetention
        val raw = stability / FsrsParameters.FACTOR * (retention.pow(1.0 / FsrsParameters.DECAY) - 1.0)
        return raw.coerceIn(MINIMUM_INTERVAL_DAYS, parameters.maximumIntervalDays)
    }

    fun nextModeEscalation(current: Int, rating: Rating): Int = when (rating) {
        Rating.AGAIN -> 0
        Rating.HARD -> current
        Rating.GOOD -> min(current + 1, MAX_MODE_ESCALATION)
        Rating.EASY -> min(current + 2, MAX_MODE_ESCALATION)
    }

    private fun constrainDifficulty(difficulty: Double): Double = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private fun elapsedDaysSince(lastReviewAt: Instant?, now: Instant): Double {
        if (lastReviewAt == null) return 0.0
        val elapsed: Duration = now - lastReviewAt
        return max(0.0, elapsed.inWholeSeconds.toDouble() / SECONDS_PER_DAY)
    }

    companion object {
        const val MIN_DIFFICULTY: Double = 1.0
        const val MAX_DIFFICULTY: Double = 10.0
        const val MINIMUM_INTERVAL_DAYS: Double = 0.0
        const val MAX_MODE_ESCALATION: Int = 3

        /** A lapsed card comes back inside the same sitting. */
        val RELEARNING_DELAY: Duration = 10.minutes

        private const val SECONDS_PER_DAY = 86_400.0
    }
}
