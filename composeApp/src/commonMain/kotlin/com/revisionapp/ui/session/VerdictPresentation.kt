package com.revisionapp.ui.session

import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.check.VerdictReason
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Turns a [VerdictReason] into a sentence a student can act on.
 *
 * Pure Kotlin with no Compose imports so the exhaustive `when` stays unit
 * testable: adding a new reason to the sealed hierarchy breaks this file's
 * compilation until it has been explained, which is the point.
 */
object VerdictPresentation {

    fun label(kind: VerdictKind): String = when (kind) {
        VerdictKind.CORRECT -> "Correct"
        VerdictKind.PARTIAL -> "Partly right"
        VerdictKind.INCORRECT -> "Incorrect"
    }

    fun describe(reason: VerdictReason): String = when (reason) {
        VerdictReason.EmptyInput -> "Nothing was entered."

        VerdictReason.ExactMatch -> "Matched the model answer exactly."

        is VerdictReason.AliasMatch -> "Matched an accepted alternative: " + reason.alias + "."

        is VerdictReason.KeyPointCoverage ->
            "Covered ${reason.matched} of ${reason.total} key points."

        is VerdictReason.NegationConflict ->
            "The answer contradicts the model answer: " + reason.modelTerm + " vs " + reason.answerTerm + "."

        is VerdictReason.NegationPolarity ->
            "Negation does not match: the model answer is " +
                polarity(reason.modelNegated) + ", the answer is " + polarity(reason.answerNegated) + "."

        is VerdictReason.NumericMatch ->
            "Within tolerance of " + number(reason.expected) + " (+/- " + number(reason.allowedError) + ")."

        is VerdictReason.NumericClose ->
            "Close to " + number(reason.expected) + " but outside the +/- " +
                number(reason.allowedError) + " tolerance."

        is VerdictReason.NumericMismatch ->
            "Expected " + number(reason.expected) + " (+/- " + number(reason.allowedError) +
                "), got " + number(reason.actual) + "."

        is VerdictReason.NumericUnparseable ->
            "Could not read a number from \"" + reason.input + "\"."

        VerdictReason.NumericNoExpectedValue ->
            "No expected value is stored for this card, so it needs your judgement."

        is VerdictReason.ExpressionMatch ->
            "Equivalent to " + reason.canonical + "."

        VerdictReason.ExpressionUndecidable ->
            "The two expressions could not be compared structurally, so it needs your judgement."

        VerdictReason.SelfGrade -> "Compare your answer with the model answer below."

        is VerdictReason.Similarity ->
            "No key points on this card; judged by similarity (" + percent(reason.cosine) + ")."

        is VerdictReason.TilePlacement ->
            "${reason.correctPositions} of ${reason.totalPositions} tiles were in the right place."

        is VerdictReason.McqSelection ->
            if (reason.selectedIndex == reason.correctIndex) {
                "That was the right option."
            } else {
                "Option ${reason.selectedIndex + 1} was chosen; option ${reason.correctIndex + 1} is correct."
            }
    }

    private fun polarity(negated: Boolean): String = if (negated) "negative" else "positive"

    /** Three decimal places at most, and no `-0.0`. */
    fun number(value: Double): String {
        val scaled = (value * 1000).roundToLong() / 1000.0
        val rounded = if (abs(scaled) < 0.0005) 0.0 else scaled
        return rounded.toString().removeSuffix(".0")
    }

    fun percent(fraction: Double): String = (fraction * 100).roundToLong().toString() + "%"
}
