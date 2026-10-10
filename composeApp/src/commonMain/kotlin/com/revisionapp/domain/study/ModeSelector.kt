package com.revisionapp.domain.study

import com.revisionapp.domain.model.AnswerType
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.StudyMode
import kotlin.random.Random

/**
 * Chooses an appropriate, supported presentation for each card and climbs the
 * easy-to-hard ladder as the card is mastered. Tile questions are retired from
 * study sessions; their model and stored card metadata remain for compatibility
 * with existing packs and review history.
 */
object ModeSelector {

    /**
     * @param mcqAvailable whether [McqQuestionFactory] can produce four options.
     * @param modeEscalation how many rungs the card has already climbed.
     */
    fun bestFit(
        card: Card,
        mcqAvailable: Boolean,
        modeEscalation: Int = 0,
    ): StudyMode = escalate(baseMode(card, mcqAvailable), mcqAvailable, modeEscalation)

    /** The mode that suits the card's content, ignoring mastery. */
    fun baseMode(card: Card, mcqAvailable: Boolean): StudyMode = when (card.answerType) {
        // Nothing to check automatically, so the user reads and rates.
        AnswerType.SELF_GRADE -> StudyMode.FLASHCARD

        // Numbers, algebra and cards with key points can be checked as typed answers.
        AnswerType.NUMERIC, AnswerType.EXPRESSION -> StudyMode.TYPED
        AnswerType.TEXT -> when {
            card.keyPoints.isNotEmpty() -> StudyMode.TYPED
            mcqAvailable -> StudyMode.MCQ
            // Avoid making a low-confidence free-text judgement when there are no
            // authored key points or plausible distractors.
            else -> StudyMode.FLASHCARD
        }
    }

    /** The supported mastery ladder, easiest first. */
    fun ladder(mcqAvailable: Boolean): List<StudyMode> =
        if (mcqAvailable) listOf(StudyMode.MCQ, StudyMode.TYPED) else listOf(StudyMode.TYPED)

    private fun escalate(base: StudyMode, mcqAvailable: Boolean, modeEscalation: Int): StudyMode {
        // Flashcards are self-rated and typed answers are already the hardest
        // supported rung, so neither escalates.
        if (base == StudyMode.FLASHCARD || base == StudyMode.TYPED) return base

        val rungs = ladder(mcqAvailable)
        val start = rungs.indexOf(base).coerceAtLeast(0)
        return rungs[minOf(start + modeEscalation, rungs.lastIndex)]
    }
}

/** Turns a card plus a resolved mode into the [Question] the UI renders. */
object QuestionFactory {

    /**
     * Null means "this mode cannot show this card" or "this retired mode is no
     * longer offered". Automatic study chooses the mode per card; an explicit
     * legacy tile request is rejected so tile answering cannot enter a session.
     */
    fun create(
        card: Card,
        mode: StudyMode,
        siblings: List<Card>,
        modeEscalation: Int = 0,
        random: Random = Random.Default,
    ): Question? {
        if (mode == StudyMode.TILES) return null

        val concrete = if (mode == StudyMode.MIXED) {
            ModeSelector.bestFit(
                card = card,
                mcqAvailable = McqQuestionFactory.isEligible(card, siblings),
                modeEscalation = modeEscalation,
            )
        } else {
            mode
        }

        return when (concrete) {
            StudyMode.FLASHCARD -> Question.Flashcard(card.id)
            StudyMode.TYPED -> Question.Typed(card.id)
            StudyMode.TILES -> null
            StudyMode.MCQ -> McqQuestionFactory.create(card, siblings, random)
            StudyMode.MIXED -> null
        }
    }
}
