package com.revisionapp.domain.sync

import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.srs.FsrsScheduler
import com.revisionapp.domain.srs.Rating
import com.revisionapp.domain.srs.ScheduleState
import kotlin.time.Instant

/**
 * One review, as it appears in an append-only log segment.
 *
 * [eventId] is the deduplication key: it is derived from the review rather than
 * generated, so the same review arriving twice -- because a segment was
 * re-uploaded, or because two devices both had it -- is stored once. That is
 * what lets a merge be repeated safely.
 *
 * [escalation] is carried along because the mode ladder is advanced by the
 * study session rather than by the scheduler, so replaying ratings alone would
 * not reproduce it. Everything else the rebuild needs is derivable.
 */
data class LoggedReview(
    val eventId: String,
    val cardId: CardId,
    val mode: StudyMode,
    val rating: Rating,
    val verdict: VerdictKind?,
    val correct: Boolean,
    val reviewedAt: Long,
    val deviceId: DeviceId,
    val escalation: Int,
) {
    val reviewedAtInstant: Instant get() = Instant.fromEpochMilliseconds(reviewedAt)

    companion object {
        /**
         * The dedup key for a review. Two devices can review the same card in
         * the same millisecond, so the device id is part of it.
         */
        fun eventIdOf(
            deviceId: DeviceId,
            cardId: CardId,
            reviewedAt: Long,
            rating: Rating,
            mode: StudyMode,
        ): String = listOf(
            deviceId.value,
            cardId.value,
            reviewedAt.toString(),
            rating.name,
            mode.name,
        ).joinToString("|")
    }
}

/**
 * Merges log segments from every device into one chronological history.
 *
 * The log is the authority and the schedule is a cache: card state can always
 * be thrown away and recomputed from the merged log by [ScheduleRebuilder],
 * which is why state never has to be merged carefully and why a corrupt or
 * half-written state row is a recoverable problem rather than lost progress.
 * See D45.
 */
object ReviewLogMerger {

    /**
     * Deduplicates by [LoggedReview.eventId] and sorts by time.
     *
     * Order within one instant is broken by event id so that the result does
     * not depend on the order the segments were read in -- two devices merging
     * the same set of segments must produce the same history, or they would
     * rebuild different schedules from the same data.
     */
    fun merge(segments: List<List<LoggedReview>>): List<LoggedReview> {
        val byEvent = LinkedHashMap<String, LoggedReview>()
        for (segment in segments) {
            for (review in segment) {
                // First writer wins for a given event id. The entries are
                // identical by construction, so this only matters when a segment
                // was written by an older build with fewer fields.
                byEvent.putIfAbsent(review.eventId, review)
            }
        }
        return byEvent.values.sortedWith(
            compareBy({ it.reviewedAt }, { it.eventId }),
        )
    }

    /** How many lines were dropped as duplicates, for the sync summary. */
    fun duplicateCount(segments: List<List<LoggedReview>>): Int =
        segments.sumOf { it.size } - merge(segments).size
}

/**
 * Recomputes every card's schedule from a merged review history.
 *
 * Replaying the log rather than trusting stored state is what makes the two
 * devices converge: after a merge, both rebuild from the same ordered events
 * and get the same answer, so a state record that was lost, stale or written by
 * an older build costs nothing.
 */
object ScheduleRebuilder {

    fun rebuild(
        reviews: List<LoggedReview>,
        scheduler: FsrsScheduler,
    ): Map<CardId, ScheduleState> {
        val states = mutableMapOf<CardId, ScheduleState>()
        for (review in reviews) {
            val now = review.reviewedAtInstant
            val previous = states[review.cardId] ?: ScheduleState.new(now)
            states[review.cardId] = scheduler.review(previous, review.rating, now)
                .copy(modeEscalation = review.escalation)
        }
        return states
    }

    /** Cards in [reviews] that have no state in [existing], i.e. new to this device. */
    fun cardsMissingFrom(reviews: List<LoggedReview>, existing: Map<CardId, ScheduleState>): List<CardId> =
        reviews.map { it.cardId }.distinct().filter { it !in existing }
}
