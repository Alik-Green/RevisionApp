package com.revisionapp.domain.check

import com.revisionapp.domain.model.AnswerType
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.KeyPoint

/**
 * The checking pipeline from the brief, in order:
 *
 * 1. normalise ([TextNormaliser]) — LaTeX, unicode maths, case, punctuation,
 *    whitespace, number words;
 * 2. exact and alias match against `back` and `acceptedAliases`;
 * 3. key-point coverage, fuzzy on typos, weighted, with every `mustInclude` point
 *    mandatory for `CORRECT`;
 * 4. the direction / negation guard ([NegationGuard]), which caps at `INCORRECT`;
 * 5. TF-IDF similarity ([TfIdfScorer]) as a soft fallback for cards with no key
 *    points;
 * 6. `NUMERIC` via [NumberParser] and [NumericComparing], `EXPRESSION` via
 *    [ExpressionComparing] with a self-grade fallback, `SELF_GRADE` handed
 *    straight to the user.
 *
 * An optional [SemanticScorer] can raise a TEXT verdict but never lower one, and
 * never overrides the negation guard.
 */
class DefaultAnswerChecker(
    private val corpus: TermCorpus? = null,
    private val semanticScorer: SemanticScorer = NoSemanticScorer,
) : AnswerChecker {

    private val similarity = TfIdfScorer(corpus)

    override fun check(card: Card, input: String, learnedAnswers: List<String>): Verdict {
        if (card.answerType == AnswerType.SELF_GRADE) return Verdict.selfGrade()
        if (input.isBlank()) {
            return Verdict.incorrect(
                reason = VerdictReason.EmptyInput,
                missedKeyPoints = card.keyPoints.map { it.text },
            )
        }
        return when (card.answerType) {
            AnswerType.TEXT -> applySemantic(card, input, checkText(card, input, learnedAnswers))
            AnswerType.NUMERIC -> checkNumeric(card, input)
            AnswerType.EXPRESSION -> checkExpression(card, input)
            AnswerType.SELF_GRADE -> Verdict.selfGrade()
        }
    }

    // ---------------------------------------------------------------- TEXT ---

    private fun checkText(card: Card, input: String, learnedAnswers: List<String>): Verdict {
        val normalisedInput = TextNormaliser.normalise(input)
        val inputTokens = TextNormaliser.matchTokens(input).toSet()

        val exact = exactOrAliasMatch(card, normalisedInput, learnedAnswers)
        if (exact != null) return exact

        val base = coverageVerdict(card, input, inputTokens, normalisedInput)

        val conflict = NegationGuard.findConflict(modelTokens(card), inputTokens) ?: return base
        val reason = when (conflict) {
            is NegationGuard.Conflict.OppositeTerm ->
                VerdictReason.NegationConflict(conflict.modelTerm, conflict.answerTerm)

            is NegationGuard.Conflict.Polarity ->
                VerdictReason.NegationPolarity(conflict.modelNegated, conflict.answerNegated)
        }
        return base.cappedAtIncorrect(reason)
    }

    /**
     * Key points are the primary evidence, but they are authored wording and a
     * correct answer in the student's own words can miss every one of them. When
     * coverage alone says INCORRECT, the answer is compared with the model answer
     * as a whole and rescued to PARTIAL if it is close enough. It never rescues to
     * CORRECT - key points still decide that - and the negation guard runs after
     * this, so a contradicting answer is capped back at INCORRECT.
     */
    private fun coverageVerdict(
        card: Card,
        input: String,
        inputTokens: Set<String>,
        normalisedInput: String,
    ): Verdict {
        if (card.keyPoints.isEmpty()) return similarityVerdict(card, input)
        val keyed = keyPointVerdict(card, inputTokens, normalisedInput)
        if (keyed.kind != VerdictKind.INCORRECT) return keyed
        val cosine = similarity.cosine(input, card.back)
        if (cosine < PARAPHRASE_RESCUE) return keyed
        return Verdict(
            kind = VerdictKind.PARTIAL,
            score = cosine,
            matchedKeyPoints = keyed.matchedKeyPoints,
            missedKeyPoints = keyed.missedKeyPoints,
            reason = VerdictReason.Similarity(cosine),
        )
    }

    private fun exactOrAliasMatch(
        card: Card,
        normalisedInput: String,
        learnedAnswers: List<String>,
    ): Verdict? {
        if (normalisedInput.isEmpty()) return null
        val normalisedBack = TextNormaliser.normalise(card.back)
        if (normalisedBack.isNotEmpty() && normalisedBack == normalisedInput) {
            return Verdict.correct(
                reason = VerdictReason.ExactMatch,
                matchedKeyPoints = card.keyPoints.map { it.text },
            )
        }
        for (alias in card.acceptedAliases) {
            val normalisedAlias = TextNormaliser.normalise(alias)
            if (normalisedAlias.isNotEmpty() && normalisedAlias == normalisedInput) {
                return Verdict.correct(
                    reason = VerdictReason.AliasMatch(alias),
                    matchedKeyPoints = card.keyPoints.map { it.text },
                )
            }
        }
        // Learned last, so an authored alias still reports itself as the reason.
        for (learned in learnedAnswers) {
            val normalisedLearned = TextNormaliser.normalise(learned)
            if (normalisedLearned.isNotEmpty() && normalisedLearned == normalisedInput) {
                return Verdict.correct(
                    reason = VerdictReason.LearnedMatch(learned),
                    matchedKeyPoints = card.keyPoints.map { it.text },
                )
            }
        }
        return null
    }

    private fun keyPointVerdict(card: Card, inputTokens: Set<String>, normalisedInput: String): Verdict {
        val matched = ArrayList<String>(card.keyPoints.size)
        val missed = ArrayList<String>()
        var matchedWeight = 0.0
        var totalWeight = 0.0

        for (keyPoint in card.keyPoints) {
            val weight = keyPoint.weight * (if (keyPoint.mustInclude) MANDATORY_MULTIPLIER else 1.0)
            totalWeight += weight
            if (keyPointMatches(keyPoint, inputTokens, normalisedInput)) {
                matched.add(keyPoint.text)
                matchedWeight += weight
            } else {
                missed.add(keyPoint.text)
            }
        }

        val score = if (totalWeight <= 0.0) 0.0 else matchedWeight / totalWeight
        val allMandatoryMatched = card.mandatoryKeyPoints.all { it.text in matched }
        val kind = when {
            allMandatoryMatched && score >= CORRECT_THRESHOLD -> VerdictKind.CORRECT
            score >= PARTIAL_THRESHOLD || matched.isNotEmpty() -> VerdictKind.PARTIAL
            else -> VerdictKind.INCORRECT
        }
        return Verdict(
            kind = kind,
            score = score,
            matchedKeyPoints = matched,
            missedKeyPoints = missed,
            reason = VerdictReason.KeyPointCoverage(matched.size, card.keyPoints.size),
        )
    }

    private fun keyPointMatches(
        keyPoint: KeyPoint,
        inputTokens: Set<String>,
        normalisedInput: String,
    ): Boolean {
        val phrases = (listOf(keyPoint.text) + keyPoint.synonyms).filter { it.isNotBlank() }
        return phrases.any { phraseMatches(it, inputTokens, normalisedInput) }
    }

    private fun phraseMatches(
        phrase: String,
        inputTokens: Set<String>,
        normalisedInput: String,
    ): Boolean {
        val normalisedPhrase = TextNormaliser.normalise(phrase)
        if (normalisedPhrase.isEmpty()) return false
        if (normalisedInput.contains(normalisedPhrase)) return true

        val phraseTokens = TextNormaliser.matchTokens(phrase)
        if (phraseTokens.isEmpty()) return false

        // A negative key point ("no net force") must not be satisfied by an
        // answer that drops the negation ("there is a net force").
        if (phraseTokens.any { NegationGuard.isNegation(it) } && !NegationGuard.isNegated(inputTokens)) {
            return false
        }

        val found = phraseTokens.count { token ->
            token in inputTokens || inputTokens.any { EditDistance.isClose(token, it) }
        }
        val coverage = found.toDouble() / phraseTokens.size
        val threshold = if (phraseTokens.size == 1) SINGLE_TOKEN_THRESHOLD else MULTI_TOKEN_THRESHOLD
        return coverage >= threshold
    }

    private fun similarityVerdict(card: Card, input: String): Verdict {
        val cosine = similarity.cosine(input, card.back)
        val kind = when {
            cosine >= SIMILARITY_CORRECT -> VerdictKind.CORRECT
            cosine >= SIMILARITY_PARTIAL -> VerdictKind.PARTIAL
            else -> VerdictKind.INCORRECT
        }
        return Verdict(
            kind = kind,
            score = cosine,
            matchedKeyPoints = emptyList(),
            missedKeyPoints = emptyList(),
            reason = VerdictReason.Similarity(cosine),
        )
    }

    private fun applySemantic(card: Card, input: String, verdict: Verdict): Verdict {
        // The guard's decision is final: a semantic model must not be allowed to
        // resurrect an answer that says the opposite of the model answer.
        if (verdict.reason is VerdictReason.NegationConflict || verdict.reason is VerdictReason.NegationPolarity) {
            return verdict
        }
        val semantic = semanticScorer.score(card.back, input) ?: return verdict
        val blended = maxOf(verdict.score, semantic.coerceIn(0.0, 1.0) * SEMANTIC_WEIGHT)
        val kind = when {
            blended >= CORRECT_THRESHOLD -> VerdictKind.CORRECT
            blended >= PARTIAL_THRESHOLD -> VerdictKind.PARTIAL
            else -> verdict.kind
        }
        return verdict.copy(kind = kind, score = blended)
    }

    /**
     * The tokens the guard compares against: the model answer and its key points,
     * but *not* the question, which routinely offers both alternatives.
     */
    private fun modelTokens(card: Card): Set<String> {
        val builder = StringBuilder(card.back)
        for (keyPoint in card.keyPoints) {
            builder.append(' ').append(keyPoint.text)
            for (synonym in keyPoint.synonyms) builder.append(' ').append(synonym)
        }
        return TextNormaliser.matchTokenSet(builder.toString())
    }

    // ------------------------------------------------------------ NUMERIC ---

    private fun checkNumeric(card: Card, input: String): Verdict {
        val spec = card.numeric
        val expected = spec?.value ?: NumberParser.parse(card.back)
        return when (val outcome = NumericComparing.compare(expected, input, spec?.tolerance)) {
            is NumericComparing.Outcome.Match -> Verdict.correct(
                reason = VerdictReason.NumericMatch(outcome.expected, outcome.actual, outcome.allowedError),
                matchedKeyPoints = card.keyPoints.map { it.text },
            )

            is NumericComparing.Outcome.Close -> Verdict(
                kind = VerdictKind.PARTIAL,
                score = CLOSE_SCORE,
                matchedKeyPoints = emptyList(),
                missedKeyPoints = card.keyPoints.map { it.text },
                reason = VerdictReason.NumericClose(outcome.expected, outcome.actual, outcome.allowedError),
            )

            is NumericComparing.Outcome.Mismatch -> Verdict.incorrect(
                reason = VerdictReason.NumericMismatch(outcome.expected, outcome.actual, outcome.allowedError),
                missedKeyPoints = card.keyPoints.map { it.text },
            )

            is NumericComparing.Outcome.Unparseable -> Verdict.incorrect(
                reason = VerdictReason.NumericUnparseable(input),
                missedKeyPoints = card.keyPoints.map { it.text },
            )

            is NumericComparing.Outcome.NoExpectedValue -> Verdict.selfGrade(
                VerdictReason.NumericNoExpectedValue,
            )
        }
    }

    // --------------------------------------------------------- EXPRESSION ---

    private fun checkExpression(card: Card, input: String): Verdict {
        val canonical = ExpressionComparing.canonicalise(input)
        if (canonical.isEmpty()) return Verdict.incorrect(VerdictReason.EmptyInput)

        val candidates = listOf(card.back) + card.acceptedAliases
        val matched = candidates.firstOrNull { ExpressionComparing.equivalent(it, input) }
        if (matched != null) {
            return Verdict.correct(
                reason = VerdictReason.ExpressionMatch(canonical),
                matchedKeyPoints = card.keyPoints.map { it.text },
            )
        }
        // Structural comparison cannot expand products, so "different" and "wrong"
        // are indistinguishable here. Hand it to the user rather than guessing.
        return Verdict.selfGrade(VerdictReason.ExpressionUndecidable)
    }

    companion object {
        /** Weighted key-point score needed for `CORRECT`. */
        const val CORRECT_THRESHOLD: Double = 0.75

        /** Weighted key-point score needed for `PARTIAL`. */
        const val PARTIAL_THRESHOLD: Double = 0.3

        /** `mustInclude` points count double towards the score. */
        const val MANDATORY_MULTIPLIER: Double = 2.0

        /** A one-token key point has to be present; longer ones may lose a word. */
        const val SINGLE_TOKEN_THRESHOLD: Double = 1.0
        const val MULTI_TOKEN_THRESHOLD: Double = 0.6

        /** Similarity is a soft signal: trusted fully only when very high. */
        const val SIMILARITY_CORRECT: Double = 0.92

        /**
         * How close to the model answer a paraphrase must be to be rescued from
         * INCORRECT to PARTIAL after failing every key point. Deliberately well
         * above [SIMILARITY_PARTIAL], which applies to cards with no key points at
         * all: here the authored evidence has already said no.
         */
        const val PARAPHRASE_RESCUE: Double = 0.75
        const val SIMILARITY_PARTIAL: Double = 0.5

        const val CLOSE_SCORE: Double = 0.5

        /** A plug-in semantic scorer is never trusted at full weight. */
        const val SEMANTIC_WEIGHT: Double = 0.95
    }
}
