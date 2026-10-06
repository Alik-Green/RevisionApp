package com.revisionapp.domain.check

/** The three-way result the brief asks for. */
enum class VerdictKind {
    CORRECT,
    PARTIAL,
    INCORRECT,
}

/**
 * Why a verdict was reached. A sealed hierarchy so that the UI can render an
 * explanation with an exhaustive `when` and so tests can assert on the exact
 * path through the checking pipeline.
 */
sealed interface VerdictReason {
    data object EmptyInput : VerdictReason
    data object ExactMatch : VerdictReason
    data class AliasMatch(val alias: String) : VerdictReason
    data class KeyPointCoverage(val matched: Int, val total: Int) : VerdictReason
    data class NegationConflict(val modelTerm: String, val answerTerm: String) : VerdictReason
    data class NegationPolarity(val modelNegated: Boolean, val answerNegated: Boolean) : VerdictReason
    data class NumericMatch(val expected: Double, val actual: Double, val allowedError: Double) : VerdictReason
    data class NumericClose(val expected: Double, val actual: Double, val allowedError: Double) : VerdictReason
    data class NumericMismatch(val expected: Double, val actual: Double, val allowedError: Double) : VerdictReason
    data class NumericUnparseable(val input: String) : VerdictReason
    data object NumericNoExpectedValue : VerdictReason
    data class ExpressionMatch(val canonical: String) : VerdictReason
    data object ExpressionUndecidable : VerdictReason
    data object SelfGrade : VerdictReason
    data class Similarity(val cosine: Double) : VerdictReason
    data class TilePlacement(val correctPositions: Int, val totalPositions: Int) : VerdictReason
    data class McqSelection(val selectedIndex: Int, val correctIndex: Int) : VerdictReason
}

/**
 * The outcome of checking one answer.
 *
 * [requiresSelfGrade] is the "we cannot decide, you judge" signal: the UI shows
 * the model answer and offers "I was right" / "I was wrong", and the resulting
 * choice — not this verdict — is what feeds spaced repetition.
 */
data class Verdict(
    val kind: VerdictKind,
    /** 0.0..1.0 confidence that the answer is right. */
    val score: Double,
    val matchedKeyPoints: List<String>,
    val missedKeyPoints: List<String>,
    val reason: VerdictReason,
    val requiresSelfGrade: Boolean = false,
) {
    val isCorrect: Boolean get() = kind == VerdictKind.CORRECT
    val isPartial: Boolean get() = kind == VerdictKind.PARTIAL

    /**
     * Caps this verdict at [VerdictKind.INCORRECT]. Used by the direction and
     * negation guard: an answer that says the opposite of the model answer must
     * not be rescued by token overlap.
     */
    fun cappedAtIncorrect(reason: VerdictReason): Verdict = Verdict(
        kind = VerdictKind.INCORRECT,
        score = minOf(score, INCORRECT_SCORE_CAP),
        matchedKeyPoints = matchedKeyPoints,
        missedKeyPoints = missedKeyPoints,
        reason = reason,
        requiresSelfGrade = false,
    )

    companion object {
        /** An overridden-to-incorrect verdict still keeps a trace of its score. */
        const val INCORRECT_SCORE_CAP: Double = 0.2

        fun incorrect(
            reason: VerdictReason,
            matchedKeyPoints: List<String> = emptyList(),
            missedKeyPoints: List<String> = emptyList(),
        ): Verdict = Verdict(VerdictKind.INCORRECT, 0.0, matchedKeyPoints, missedKeyPoints, reason)

        fun correct(
            reason: VerdictReason,
            matchedKeyPoints: List<String> = emptyList(),
            missedKeyPoints: List<String> = emptyList(),
        ): Verdict = Verdict(VerdictKind.CORRECT, 1.0, matchedKeyPoints, missedKeyPoints, reason)

        /** "Show the model answer, you decide." */
        fun selfGrade(reason: VerdictReason = VerdictReason.SelfGrade): Verdict = Verdict(
            kind = VerdictKind.PARTIAL,
            score = SELF_GRADE_SCORE,
            matchedKeyPoints = emptyList(),
            missedKeyPoints = emptyList(),
            reason = reason,
            requiresSelfGrade = true,
        )

        const val SELF_GRADE_SCORE: Double = 0.5
    }
}
