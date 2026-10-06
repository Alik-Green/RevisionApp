package com.revisionapp.domain.check

/**
 * The direction / negation guard.
 *
 * Token-overlap scoring cannot tell "the current increases" from "the current
 * decreases": both answers share almost every token, so a similarity scorer rates
 * the wrong one highly. This is the single biggest source of false positives in
 * the checker, so it is kept in one small, heavily tested place.
 *
 * Two independent signals, both compared on *stemmed* tokens:
 *
 * 1. **Opposite terms.** If the model answer contains exactly one side of a known
 *    opposite pair and the answer contains the other side, the answer contradicts
 *    the model. Pairs where the model contains *both* sides ("unlike series
 *    circuits, in parallel ...") are skipped, because there is no usable signal.
 * 2. **Negation polarity.** If the model answer is negated and the answer is not,
 *    or vice versa, the answer contradicts the model.
 *
 * The guard only ever *caps* a verdict; it never promotes one. A false cap is
 * recoverable because the UI always offers "I was right", whereas a false
 * CORRECT is not.
 */
object NegationGuard {

    sealed interface Conflict {
        /** The model said [modelTerm]; the answer said [answerTerm]. */
        data class OppositeTerm(val modelTerm: String, val answerTerm: String) : Conflict

        /** One of the two is negated and the other is not. */
        data class Polarity(val modelNegated: Boolean, val answerNegated: Boolean) : Conflict
    }

    private val OPPOSITE_PAIRS: List<Pair<String, String>> = listOf(
        "increase" to "decrease",
        "greater" to "less",
        "larger" to "smaller",
        "bigger" to "smaller",
        "more" to "less",
        "higher" to "lower",
        "longer" to "shorter",
        "faster" to "slower",
        "stronger" to "weaker",
        "above" to "below",
        "positive" to "negative",
        "parallel" to "perpendicular",
        "series" to "parallel",
        "same" to "opposite",
        "same" to "different",
        "clockwise" to "anticlockwise",
        "clockwise" to "counterclockwise",
        "forwards" to "backwards",
        "upwards" to "downwards",
        "up" to "down",
        "towards" to "away",
        "attract" to "repel",
        "converge" to "diverge",
        "absorb" to "emit",
        "endothermic" to "exothermic",
        "oxidation" to "reduction",
        "constructive" to "destructive",
        "real" to "virtual",
        "upright" to "inverted",
        "magnified" to "diminished",
        "true" to "false",
        "valid" to "invalid",
        "north" to "south",
        "east" to "west",
        "input" to "output",
        "gain" to "loss",
        "compress" to "expand",
    ).map { (left, right) -> Stemmer.stem(left) to Stemmer.stem(right) }

    private val NEGATIONS: Set<String> = setOf(
        "not", "no", "never", "nor", "neither", "none", "nothing", "nobody",
        "cannot", "cant", "dont", "doesnt", "didnt", "isnt", "arent", "wasnt",
        "werent", "wont", "wouldnt", "couldnt", "shouldnt", "havent", "hasnt",
        "hadnt", "without", "absence", "false",
    ).map { Stemmer.stem(it) }.toSet()

    /** The stemmed tokens that count as a negation, exposed for tests. */
    val negationTokens: Set<String> get() = NEGATIONS

    fun isNegation(token: String): Boolean = token in NEGATIONS

    fun isNegated(tokens: Iterable<String>): Boolean = tokens.any { it in NEGATIONS }

    /**
     * @param modelTokens stemmed tokens of the model answer — `back` plus the key
     *   points, and deliberately *not* the question, which often offers both
     *   alternatives ("does the resistance increase or decrease?").
     * @param answerTokens stemmed tokens of what the user typed.
     */
    fun findConflict(modelTokens: Set<String>, answerTokens: Set<String>): Conflict? {
        for ((left, right) in OPPOSITE_PAIRS) {
            val modelHasLeft = left in modelTokens
            val modelHasRight = right in modelTokens
            // Both or neither: the pair carries no signal for this card.
            if (modelHasLeft == modelHasRight) continue

            val modelTerm = if (modelHasLeft) left else right
            val opposite = if (modelHasLeft) right else left
            if (opposite in answerTokens) return Conflict.OppositeTerm(modelTerm, opposite)
        }

        val modelNegated = isNegated(modelTokens)
        val answerNegated = isNegated(answerTokens)
        if (modelNegated != answerNegated) return Conflict.Polarity(modelNegated, answerNegated)

        return null
    }
}
