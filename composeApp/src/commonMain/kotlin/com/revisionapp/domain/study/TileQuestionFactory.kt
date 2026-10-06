package com.revisionapp.domain.study

import com.revisionapp.domain.check.TextNormaliser
import com.revisionapp.domain.model.AnswerType
import com.revisionapp.domain.model.Card
import kotlin.random.Random

/**
 * Builds Duolingo-style tile questions.
 *
 * The brief is explicit that tiles are only for *short* answers: either the
 * author supplied a chunked [Card.tileAnswer] of at most [MAX_CHUNKS] pieces, or
 * the model answer tokenises to at most [MAX_TOKENS] words. A long paragraph is
 * never exploded into single-word tiles — such cards are silently skipped rather
 * than turned into an unusable puzzle.
 */
object TileQuestionFactory {

    const val MIN_CHUNKS: Int = 2
    const val MAX_CHUNKS: Int = 8
    const val MIN_TOKENS: Int = 2
    const val MAX_TOKENS: Int = 12
    const val DECOY_COUNT: Int = 2

    /** Decoy words shorter than this are noise rather than plausible distractors. */
    private const val MIN_DECOY_LENGTH: Int = 4

    /**
     * The ordered chunks the answer is built from, or null when the card does not
     * qualify for tile mode.
     */
    fun chunksFor(card: Card): List<String>? {
        val authored = card.tileAnswer?.map { it.trim() }?.filter { it.isNotEmpty() }
        if (authored != null) {
            return if (authored.size in MIN_CHUNKS..MAX_CHUNKS) authored else null
        }
        // There is nothing to build for a card the user has to judge themselves.
        if (card.answerType == AnswerType.SELF_GRADE) return null
        val tokens = TextNormaliser.tokens(card.back)
        return if (tokens.size in MIN_TOKENS..MAX_TOKENS) tokens else null
    }

    fun isEligible(card: Card): Boolean = chunksFor(card) != null

    /**
     * @param siblings cards in the same topic, used as the decoy source. Fewer
     *   usable decoys simply means fewer tiles; the question is still playable.
     */
    fun create(card: Card, siblings: List<Card>, random: Random = Random.Default): Question.Tiles? {
        val solution = chunksFor(card) ?: return null
        val solutionText = solution.joinToString(" ").lowercase()

        val decoys = siblings.asSequence()
            .filter { it.id != card.id }
            .flatMap { TextNormaliser.tokens(it.back).asSequence() }
            .map { it.lowercase() }
            .filter { it.length >= MIN_DECOY_LENGTH }
            .filter { token -> !solutionText.contains(token) }
            .distinct()
            .take(DECOY_COUNT)
            .toList()

        // Ids are assigned in solution order *before* shuffling, so the tiles can
        // always be rebuilt into the right answer.
        val solutionTiles = solution.mapIndexed { index, text -> Tile(index, text, isDecoy = false) }
        val decoyTiles = decoys.mapIndexed { index, text -> Tile(solution.size + index, text, isDecoy = true) }

        return Question.Tiles(
            cardId = card.id,
            tiles = (solutionTiles + decoyTiles).shuffled(random),
            solution = solution,
        )
    }
}
