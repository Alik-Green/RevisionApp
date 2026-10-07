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
            .distinctBy { displayKey(it) }
        if (options.size != Mcq.OPTION_COUNT) return null

        val shuffled = options.shuffled(random).mapIndexed { index, text ->
            McqOption(index = index, text = text, isCorrect = text == correct)
        }
        // Exactly one correct answer, always.
        if (shuffled.count { it.isCorrect } != 1) return null

        return Question.MultipleChoice(cardId = card.id, options = shuffled)
    }

    private fun siblingDistractors(card: Card, siblings: List<Card>, random: Random): List<String>? {
        val correct = displayKey(card.back)
        val candidates = siblings.asSequence()
            .filter { it.id != card.id }
            .filter { it.answerType == card.answerType }
            .map { it.back.trim() }
            .filter { it.isNotEmpty() }
            .filter { displayKey(it) != correct }
            .distinctBy { displayKey(it) }
            .toList()
        if (candidates.size < Mcq.MIN_DISTRACTORS) return null
        return candidates.shuffled(random).take(Mcq.MIN_DISTRACTORS)
    }
}

/**
 * Two options are duplicates only if a student would read them as the same text.
 *
 * [TextNormaliser.normalise] is deliberately lossy - it exists for grading, so it
 * drops apostrophes and signs - and using it here collapsed `f(a)` with `f'(a)`
 * and `-a` with `a`, which quietly dropped whole cards out of multiple-choice
 * mode. This keeps LaTeX flattened to the same plain text the UI renders, so
 * `\frac{1}{2}` and `(1)/(2)` still count as one option.
 */
private val WHITESPACE_RUN = Regex("""\s+""")

private fun displayKey(option: String): String =
    WHITESPACE_RUN.replace(TextNormaliser.toPlainText(option).lowercase(), " ").trim()
