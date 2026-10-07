package com.revisionapp.domain.study

import com.revisionapp.domain.model.CardId

/** One tappable word tile. [isDecoy] tiles are never part of the answer. */
data class Tile(
    val id: Int,
    val text: String,
    val isDecoy: Boolean,
)

/** One multiple-choice option, in the order it is displayed. */
data class McqOption(
    val index: Int,
    val text: String,
    val isCorrect: Boolean,
)

/**
 * A question is always *derived* from a card and never stored, so a single card
 * powers flashcards, typed answers, word tiles and multiple choice.
 *
 * Sealed so that every screen renders it with an exhaustive `when`.
 */
sealed interface Question {
    val cardId: CardId

    /** Front, then back, then a self-rating. */
    data class Flashcard(override val cardId: CardId) : Question

    /** Type an answer; graded by the AnswerChecker, always overridable. */
    data class Typed(override val cardId: CardId) : Question

    /** Rebuild the answer from shuffled tiles, two of which are decoys. */
    data class Tiles(
        override val cardId: CardId,
        val tiles: List<Tile>,
        val solution: List<String>,
    ) : Question {
        val decoyCount: Int get() = tiles.count { it.isDecoy }
    }

    /** Pick one of four options. */
    data class MultipleChoice(
        override val cardId: CardId,
        val options: List<McqOption>,
    ) : Question {
        val correctIndex: Int get() = options.first { it.isCorrect }.index
        val correctText: String get() = options.first { it.isCorrect }.text
    }
}
