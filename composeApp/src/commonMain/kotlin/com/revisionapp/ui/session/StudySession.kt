package com.revisionapp.ui.session

import com.revisionapp.domain.check.AnswerChecker
import com.revisionapp.domain.check.Verdict
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.repository.ReviewEntry
import com.revisionapp.domain.srs.FsrsScheduler
import com.revisionapp.domain.srs.ModeGrading
import com.revisionapp.domain.srs.Rating
import com.revisionapp.domain.srs.ScheduleState
import com.revisionapp.domain.study.McqGrader
import com.revisionapp.domain.study.Question
import com.revisionapp.domain.study.TileGrader
import kotlin.time.Clock
import kotlin.time.Instant

/** One card in a session, with the mode and question actually derived for it. */
data class SessionCard(
    val card: Card,
    val mode: StudyMode,
    val question: Question,
    val schedule: ScheduleState,
)

/** What the user has done with the current card so far. */
sealed interface AnswerDraft {
    /** A flashcard that has not been turned over yet. */
    data object Hidden : AnswerDraft

    /** A flashcard that has been turned over; waiting for a rating. */
    data object Revealed : AnswerDraft

    /** A typed answer being composed. */
    data class Text(val value: String) : AnswerDraft

    /** Tiles tapped so far, in order, by tile id. */
    data class Tiles(val chosen: List<Int>) : AnswerDraft

    /** Nothing to draft: multiple choice is answered by tapping an option. */
    data object Choice : AnswerDraft
}

data class SessionSummary(
    val reviewed: Int,
    val correct: Int,
    val partial: Int,
    val incorrect: Int,
) {
    val accuracy: Double get() = if (reviewed == 0) 0.0 else correct.toDouble() / reviewed
}

/** Exhaustive UI state for a study session. */
sealed interface SessionState {
    /** The filter and mode produced nothing to study. */
    data object Empty : SessionState

    data class Asking(
        val position: Int,
        val total: Int,
        val item: SessionCard,
        val draft: AnswerDraft,
    ) : SessionState

    /** The verdict is on screen; the user rates it, overrides it, or moves on. */
    data class Reviewing(
        val position: Int,
        val total: Int,
        val item: SessionCard,
        val verdict: Verdict,
        /**
         * What the user actually typed, kept so that "I was right" can teach the
         * checker. Empty for modes whose answer is a tap rather than text.
         */
        val input: String = "",
    ) : SessionState

    data class Finished(val summary: SessionSummary) : SessionState
}

/** Every input a session screen can produce. */
sealed interface SessionEvent {
    data object Reveal : SessionEvent
    data class Type(val text: String) : SessionEvent
    data object SubmitText : SessionEvent
    data class TapTile(val tileId: Int) : SessionEvent
    data object ClearTiles : SessionEvent
    data object SubmitTiles : SessionEvent
    data class ChooseOption(val index: Int) : SessionEvent
    data class Rate(val rating: Rating) : SessionEvent
    data class Override(val userSaysCorrect: Boolean) : SessionEvent
    data object Next : SessionEvent
}

/**
 * The session state machine: unidirectional, [SessionEvent] in and
 * [SessionState] out. Every path that ends a card funnels through [commit], so
 * all four modes feed the same spaced-repetition schedule.
 */
class StudySession(
    private val items: List<SessionCard>,
    private val checker: AnswerChecker,
    private val scheduler: FsrsScheduler,
    private val clock: Clock,
    private val persistSchedule: (cardId: CardId, state: ScheduleState) -> Unit,
    private val recordReview: (entry: ReviewEntry) -> Unit,
    /** Answers the user has vouched for before, loaded once when the session is built. */
    private val learned: Map<CardId, List<String>> = emptyMap(),
    /** Called when the user overrides a typed verdict to "I was right". */
    private val onLearn: (cardId: CardId, answer: String) -> Unit = { _, _ -> },
) {
    var state: SessionState = if (items.isEmpty()) {
        SessionState.Empty
    } else {
        asking(0)
    }
        private set

    private var reviewedCount = 0
    private var correctCount = 0
    private var partialCount = 0
    private var incorrectCount = 0

    fun onEvent(event: SessionEvent) {
        when (val current = state) {
            is SessionState.Empty -> Unit
            is SessionState.Finished -> Unit
            is SessionState.Asking -> onAskingEvent(current, event)
            is SessionState.Reviewing -> onReviewingEvent(current, event)
        }
    }

    private fun onAskingEvent(asking: SessionState.Asking, event: SessionEvent) {
        when (event) {
            SessionEvent.Reveal -> if (asking.draft == AnswerDraft.Hidden) {
                state = asking.copy(draft = AnswerDraft.Revealed)
            }

            is SessionEvent.Type -> if (asking.draft is AnswerDraft.Text) {
                state = asking.copy(draft = AnswerDraft.Text(event.text))
            }

            SessionEvent.SubmitText -> submitText(asking)

            is SessionEvent.TapTile -> tapTile(asking, event.tileId)

            SessionEvent.ClearTiles -> if (asking.draft is AnswerDraft.Tiles) {
                state = asking.copy(draft = AnswerDraft.Tiles(emptyList()))
            }

            SessionEvent.SubmitTiles -> submitTiles(asking)

            is SessionEvent.ChooseOption -> chooseOption(asking, event.index)

            is SessionEvent.Rate -> if (asking.draft == AnswerDraft.Revealed) {
                commit(asking.position, asking.item, event.rating, null, event.rating != Rating.AGAIN)
            }

            is SessionEvent.Override, SessionEvent.Next -> Unit
        }
    }

    private fun onReviewingEvent(reviewing: SessionState.Reviewing, event: SessionEvent) {
        when (event) {
            is SessionEvent.Rate -> commit(
                position = reviewing.position,
                item = reviewing.item,
                rating = event.rating,
                verdictKind = reviewing.verdict.kind,
                correct = event.rating != Rating.AGAIN,
            )

            is SessionEvent.Override -> {
                if (event.userSaysCorrect) learnFrom(reviewing)
                commit(
                    position = reviewing.position,
                    item = reviewing.item,
                    rating = ModeGrading.ratingForOverride(reviewing.item.mode, event.userSaysCorrect),
                    verdictKind = reviewing.verdict.kind,
                    correct = event.userSaysCorrect,
                )
            }

            SessionEvent.Next -> if (reviewing.verdict.requiresSelfGrade) {
                // The checker could not decide; only the user can, so Next is inert
                // and the screen shows "I was right" / "I was wrong" instead.
                Unit
            } else {
                commit(
                    position = reviewing.position,
                    item = reviewing.item,
                    rating = ModeGrading.ratingFor(reviewing.item.mode, reviewing.verdict.kind),
                    verdictKind = reviewing.verdict.kind,
                    correct = reviewing.verdict.kind != VerdictKind.INCORRECT,
                )
            }

            else -> Unit
        }
    }

    private fun asking(position: Int): SessionState.Asking =
        SessionState.Asking(position, items.size, items[position], initialDraft(items[position]))

    private fun initialDraft(item: SessionCard): AnswerDraft = when (item.question) {
        is Question.Flashcard -> AnswerDraft.Hidden
        is Question.Typed -> AnswerDraft.Text("")
        is Question.Tiles -> AnswerDraft.Tiles(emptyList())
        is Question.MultipleChoice -> AnswerDraft.Choice
    }

    private fun submitText(asking: SessionState.Asking) {
        val draft = asking.draft
        if (draft !is AnswerDraft.Text) return
        state = reviewing(
            asking,
            checker.check(asking.item.card, draft.value, learned[asking.item.card.id].orEmpty()),
            draft.value,
        )
    }

    private fun tapTile(asking: SessionState.Asking, tileId: Int) {
        val draft = asking.draft
        if (draft !is AnswerDraft.Tiles) return
        val chosen = if (tileId in draft.chosen) draft.chosen - tileId else draft.chosen + tileId
        state = asking.copy(draft = AnswerDraft.Tiles(chosen))
    }

    private fun submitTiles(asking: SessionState.Asking) {
        val draft = asking.draft
        val question = asking.item.question
        if (draft !is AnswerDraft.Tiles || question !is Question.Tiles) return
        val chosenText = draft.chosen.mapNotNull { id -> question.tiles.firstOrNull { it.id == id }?.text }
        state = reviewing(asking, TileGrader.grade(question.solution, chosenText))
    }

    private fun chooseOption(asking: SessionState.Asking, index: Int) {
        val question = asking.item.question
        if (question !is Question.MultipleChoice) return
        state = reviewing(asking, McqGrader.grade(question, index))
    }

    private fun reviewing(
        asking: SessionState.Asking,
        verdict: Verdict,
        input: String = "",
    ): SessionState.Reviewing =
        SessionState.Reviewing(asking.position, asking.total, asking.item, verdict, input)

    /**
     * "I was right" on a typed answer teaches the checker.
     *
     * The wording is stored beside the card and matched ahead of key points from
     * then on, so the same answer is not marked wrong twice. Only typed answers
     * learn: a multiple-choice or tile verdict is not in doubt, so an override
     * there carries no new information about wording.
     */
    private fun learnFrom(reviewing: SessionState.Reviewing) {
        if (reviewing.item.mode != StudyMode.TYPED) return
        if (reviewing.input.isBlank()) return
        onLearn(reviewing.item.card.id, reviewing.input.trim())
    }

    /**
     * The single place where a review touches the schedule. Every mode gets here,
     * which is what makes "all modes feed the same schedule" true rather than
     * aspirational.
     */
    private fun commit(
        position: Int,
        item: SessionCard,
        rating: Rating,
        verdictKind: VerdictKind?,
        correct: Boolean,
    ) {
        val now: Instant = clock.now()
        persistSchedule(item.card.id, scheduler.review(item.schedule, rating, now))
        recordReview(ReviewEntry(item.card.id, item.mode, rating, verdictKind, correct, now))

        reviewedCount++
        when (verdictKind) {
            VerdictKind.CORRECT -> correctCount++
            VerdictKind.PARTIAL -> partialCount++
            VerdictKind.INCORRECT -> incorrectCount++
            // A flashcard rating has no verdict: Again counts as wrong, the rest as right.
            null -> if (rating == Rating.AGAIN) incorrectCount++ else correctCount++
        }

        val nextPosition = position + 1
        state = if (nextPosition >= items.size) {
            SessionState.Finished(SessionSummary(reviewedCount, correctCount, partialCount, incorrectCount))
        } else {
            asking(nextPosition)
        }
    }

    companion object {
    }
}
