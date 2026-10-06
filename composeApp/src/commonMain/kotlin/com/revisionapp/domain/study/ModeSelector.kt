package com.revisionapp.domain.study

import com.revisionapp.domain.model.AnswerType
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.StudyMode
import kotlin.random.Random

/**
 * Chooses the mode that fits a card best, and climbs the easy-to-hard ladder as
 * the card is mastered. This is what makes "Mixed" a real mode rather than a
 * random pick, and what makes a correct answer in a harder mode count for more:
 * success moves the card *up* the ladder, so it gets tested by typing eventually.
 */
object ModeSelector {

    /**
     * @param tilesAvailable whether [TileQuestionFactory] accepts this card.
     * @param mcqAvailable whether [McqQuestionFactory] can produce four options.
     * @param modeEscalation how many rungs the card has already climbed.
     */
    fun bestFit(
        card: Card,
        tilesAvailable: Boolean,
        mcqAvailable: Boolean,
        modeEscalation: Int = 0,
    ): StudyMode = escalate(baseMode(card, tilesAvailable, mcqAvailable), tilesAvailable, mcqAvailable, modeEscalation)

    /** The mode that suits the card's content, ignoring mastery. */
    fun baseMode(card: Card, tilesAvailable: Boolean, mcqAvailable: Boolean): StudyMode = when (card.answerType) {
        // Nothing to check automatically, so the user reads and rates.
        AnswerType.SELF_GRADE -> StudyMode.FLASHCARD

        // Numbers and algebra have to be typed to be checked properly.
        AnswerType.NUMERIC, AnswerType.EXPRESSION -> StudyMode.TYPED

        AnswerType.TEXT -> when {
            // Key points make typed checking meaningful, so prefer the hard mode.
            card.keyPoints.isNotEmpty() -> StudyMode.TYPED
            tilesAvailable -> StudyMode.TILES
            mcqAvailable -> StudyMode.MCQ
            else -> StudyMode.FLASHCARD
        }
    }

    /** The modes this card can actually be shown in, easiest first. */
    fun ladder(tilesAvailable: Boolean, mcqAvailable: Boolean): List<StudyMode> = buildList {
        if (mcqAvailable) add(StudyMode.MCQ)
        if (tilesAvailable) add(StudyMode.TILES)
        add(StudyMode.TYPED)
    }

    private fun escalate(
        base: StudyMode,
        tilesAvailable: Boolean,
        mcqAvailable: Boolean,
        modeEscalation: Int,
    ): StudyMode {
        // Flashcards are self-rated and typed answers are already the hardest
        // rung, so neither escalates.
        if (base == StudyMode.FLASHCARD || base == StudyMode.TYPED) return base

        val rungs = ladder(tilesAvailable, mcqAvailable)
        val start = rungs.indexOf(base).coerceAtLeast(0)
        return rungs[minOf(start + modeEscalation, rungs.lastIndex)]
    }
}

/** Turns a card plus a resolved mode into the [Question] the UI renders. */
object QuestionFactory {

    /**
     * Null means "this mode cannot show this card" — tile mode for a long
     * paragraph, or MCQ with fewer than three plausible siblings. Sessions skip
     * those cards silently.
     */
    fun create(
        card: Card,
        mode: StudyMode,
        siblings: List<Card>,
        modeEscalation: Int = 0,
        random: Random = Random.Default,
    ): Question? {
        val concrete = if (mode == StudyMode.MIXED) {
            ModeSelector.bestFit(
                card = card,
                tilesAvailable = TileQuestionFactory.isEligible(card),
                mcqAvailable = McqQuestionFactory.isEligible(card, siblings),
                modeEscalation = modeEscalation,
            )
        } else {
            mode
        }

        return when (concrete) {
            StudyMode.FLASHCARD -> Question.Flashcard(card.id)
            StudyMode.TYPED -> Question.Typed(card.id)
            StudyMode.TILES -> TileQuestionFactory.create(card, siblings, random)
            StudyMode.MCQ -> McqQuestionFactory.create(card, siblings, random)
            StudyMode.MIXED -> null
        }
    }
}
