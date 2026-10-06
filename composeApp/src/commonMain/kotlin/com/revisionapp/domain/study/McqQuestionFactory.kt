package com.revisionapp.domain.study

import com.revisionapp.domain.check.TextNormaliser
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.Mcq
import kotlin.random.Random

/**
 * Builds four-option multiple choice questions.
 *
 * Authored distractors win. When a card has none, distractors are taken from
 * sibling cards in the same topic with the same [com.revisionapp.domain.model.AnswerType]
 * — and only when at least three plausible siblings exist, as the brief requires.
 * A thin topic therefore loses MCQ silently instead of inventing options that
 * make the answer obvious.
 */
object McqQuestionFactory {

    fun isEligible(card: Card, siblings: List<Card>): Boolean = create(card, siblings) != null

    fun create(card: Card, siblings: List<Card>, random: Random = Random.Default): Question.MultipleChoice? {
        val authored = card.mcq?.takeIf { it.isUsable }
        val correct = (authored?.correct ?: card.back).trim()
        if (correct.isEmpty()) return null

        val distractors = authored?.distractors?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.take(Mcq.MIN_DISTRACTORS)
            ?: siblingDistractors(card, siblings, random)
        if (distractors == null || distractors.size < Mcq.MIN_DISTRACTORS) return null

        val options = (listOf(correct) + distractors)
            .distinctBy { TextNormaliser.normalise(it) }
        if (options.size != Mcq.OPTION_COUNT) return null

        val shuffled = options.shuffled(random).mapIndexed { index, text ->
            McqOption(index = index, text = text, isCorrect = text == correct)
        }
        // Exactly one correct answer, always.
        if (shuffled.count { it.isCorrect } != 1) return null

        return Question.MultipleChoice(cardId = card.id, options = shuffled)
    }

    private fun siblingDistractors(card: Card, siblings: List<Card>, random: Random): List<String>? {
        val correct = TextNormaliser.normalise(card.back)
        val candidates = siblings.asSequence()
            .filter { it.id != card.id }
            .filter { it.answerType == card.answerType }
            .map { it.back.trim() }
            .filter { it.isNotEmpty() }
            .filter { TextNormaliser.normalise(it) != correct }
            .distinctBy { TextNormaliser.normalise(it) }
            .toList()
        if (candidates.size < Mcq.MIN_DISTRACTORS) return null
        return candidates.shuffled(random).take(Mcq.MIN_DISTRACTORS)
    }
}
