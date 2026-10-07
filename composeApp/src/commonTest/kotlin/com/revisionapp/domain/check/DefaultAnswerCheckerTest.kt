package com.revisionapp.domain.check

import com.revisionapp.domain.model.AnswerType
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.KeyPoint
import com.revisionapp.domain.model.NumericSpec
import com.revisionapp.domain.model.TopicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultAnswerCheckerTest {

    private val checker = DefaultAnswerChecker()

    private fun textCard(
        back: String,
        front: String = "A question?",
        keyPoints: List<KeyPoint> = emptyList(),
        aliases: List<String> = emptyList(),
    ): Card = Card(
        id = CardId("user:card"),
        topicId = TopicId("user:topic"),
        front = front,
        back = back,
        answerType = AnswerType.TEXT,
        keyPoints = keyPoints,
        acceptedAliases = aliases,
    )

    private fun numericCard(back: String, value: Double? = null, tolerance: Double? = null): Card = Card(
        id = CardId("user:numeric"),
        topicId = TopicId("user:topic"),
        front = "Calculate the value.",
        back = back,
        answerType = AnswerType.NUMERIC,
        numeric = NumericSpec(value = value, tolerance = tolerance),
    )

    private fun expressionCard(back: String): Card = Card(
        id = CardId("user:expression"),
        topicId = TopicId("user:topic"),
        front = "Simplify.",
        back = back,
        answerType = AnswerType.EXPRESSION,
    )

    // ------------------------------------------------- step 2: exact / alias ---

    @Test
    fun anExactMatchIsCorrectRegardlessOfCaseAndPunctuation() {
        val card = textCard("Centripetal force.")
        val verdict = checker.check(card, "centripetal force")

        assertEquals(VerdictKind.CORRECT, verdict.kind)
        assertEquals(VerdictReason.ExactMatch, verdict.reason)
        assertEquals(1.0, verdict.score, 1e-9)
    }

    @Test
    fun anAcceptedAliasIsCorrect() {
        val card = textCard("centre-seeking force", aliases = listOf("centripetal force"))
        val verdict = checker.check(card, "Centripetal Force")

        assertEquals(VerdictKind.CORRECT, verdict.kind)
        assertIs<VerdictReason.AliasMatch>(verdict.reason)
    }

    @Test
    fun latexInTheModelAnswerMatchesPlainTextInput() {
        val card = textCard("\u0024v = \\sqrt{2gh}\u0024")
        assertEquals(VerdictKind.CORRECT, checker.check(card, "v = sqrt(2gh)").kind)
    }

    // ------------------------------------------- step 3: key-point coverage ---

    @Test
    fun everyKeyPointMatchedIsCorrect() {
        val card = textCard(
            back = "the current is proportional to the potential difference and inversely " +
                "proportional to the resistance",
            keyPoints = listOf(
                KeyPoint("current", mustInclude = true),
                KeyPoint(
                    "proportional to potential difference",
                    synonyms = listOf("proportional to pd", "proportional to voltage"),
                    mustInclude = true,
                ),
                KeyPoint("inversely proportional to resistance", mustInclude = true),
            ),
        )
        val verdict = checker.check(card, "current is proportional to pd and inversely proportional to resistance")

        assertEquals(VerdictKind.CORRECT, verdict.kind)
        assertEquals(3, verdict.matchedKeyPoints.size)
        assertTrue(verdict.missedKeyPoints.isEmpty())
    }

    @Test
    fun aMissedMandatoryKeyPointCapsTheVerdictAtPartial() {
        val card = textCard(
            back = "the current is proportional to the potential difference and inversely " +
                "proportional to the resistance",
            keyPoints = listOf(
                KeyPoint("current", mustInclude = true),
                KeyPoint("proportional to potential difference", synonyms = listOf("proportional to pd"), mustInclude = true),
                KeyPoint("inversely proportional to resistance", mustInclude = true),
            ),
        )
        val verdict = checker.check(card, "current is proportional to pd")

        assertEquals(VerdictKind.PARTIAL, verdict.kind)
        assertEquals(listOf("inversely proportional to resistance"), verdict.missedKeyPoints)
    }

    @Test
    fun aMissedOptionalKeyPointStillAllowsCorrectWhenTheScoreIsHigh() {
        val card = textCard(
            back = "the wire heats up because of its resistance",
            keyPoints = listOf(
                KeyPoint("resistance", mustInclude = true),
                KeyPoint("wire"),
                KeyPoint("heats"),
            ),
        )

        val complete = checker.check(card, "the wire heats because of its resistance")
        assertEquals(VerdictKind.CORRECT, complete.kind)

        // The mandatory point is missing, so this can never be CORRECT.
        val incomplete = checker.check(card, "the wire heats")
        assertEquals(VerdictKind.PARTIAL, incomplete.kind)
        assertEquals(listOf("resistance"), incomplete.missedKeyPoints)
    }

    @Test
    fun typosAreTolerated() {
        val card = textCard("the centripetal force", keyPoints = listOf(KeyPoint("centripetal", mustInclude = true)))

        assertEquals(VerdictKind.CORRECT, checker.check(card, "the centripetel force").kind)
        assertEquals(VerdictKind.CORRECT, checker.check(card, "centripital force").kind)
    }

    @Test
    fun synonymsMatch() {
        val card = textCard(
            back = "the kinetic energy of the object",
            keyPoints = listOf(KeyPoint("kinetic energy", synonyms = listOf("energy of motion"), mustInclude = true)),
        )

        assertEquals(VerdictKind.CORRECT, checker.check(card, "the energy of motion increases").kind)
    }

    @Test
    fun aNegativeKeyPointIsNotSatisfiedByAnAffirmativeAnswer() {
        val card = textCard(
            back = "there is no resultant force",
            keyPoints = listOf(KeyPoint("no resultant force", mustInclude = true)),
        )
        val verdict = checker.check(card, "there is a resultant force")

        assertFalse(verdict.isCorrect)
        assertEquals(listOf("no resultant force"), verdict.missedKeyPoints)
    }

    @Test
    fun anOverLongAnswerThatContainsEverythingIsStillCorrect() {
        val card = textCard(
            back = "the centripetal force acts towards the centre of the circle",
            keyPoints = listOf(
                KeyPoint("centripetal force", mustInclude = true),
                KeyPoint("towards the centre", mustInclude = true),
            ),
        )
        val verbose = "the centripetal force acts towards the centre of the circle and this long " +
            "answer also includes a great deal of extra material about the situation which should " +
            "be ignored by the checker because every required point is present"

        val verdict = checker.check(card, verbose)

        assertEquals(VerdictKind.CORRECT, verdict.kind)
        assertEquals(2, verdict.matchedKeyPoints.size)
    }

    @Test
    fun emptyAndBlankInputAreIncorrectAndListEveryKeyPointAsMissed() {
        val card = textCard(
            back = "the centripetal force",
            keyPoints = listOf(KeyPoint("centripetal", mustInclude = true), KeyPoint("force")),
        )

        for (input in listOf("", "   ", "\n\t")) {
            val verdict = checker.check(card, input)
            assertEquals(VerdictKind.INCORRECT, verdict.kind, "input=[$input]")
            assertEquals(VerdictReason.EmptyInput, verdict.reason)
            assertEquals(2, verdict.missedKeyPoints.size)
            assertEquals(0.0, verdict.score, 1e-9)
        }
    }

    @Test
    fun anUnrelatedAnswerIsIncorrect() {
        val card = textCard(
            back = "the centripetal force acts towards the centre",
            keyPoints = listOf(KeyPoint("centripetal force", mustInclude = true)),
        )

        assertEquals(VerdictKind.INCORRECT, checker.check(card, "a banana").kind)
    }

    // ---------------------------------- step 4: direction / negation guard ---

    @Test
    fun anOppositeDirectionIsCappedAtIncorrectEvenWhenKeyPointsMatch() {
        val card = textCard(
            back = "the current increases",
            // The question offers both alternatives; it must not be used as the
            // model answer, or the guard would see both sides and stand down.
            front = "Does the current increase or decrease when the resistance falls?",
            keyPoints = listOf(KeyPoint("current", mustInclude = true)),
        )

        val wrong = checker.check(card, "the current decreases")
        assertEquals(VerdictKind.INCORRECT, wrong.kind)
        assertIs<VerdictReason.NegationConflict>(wrong.reason)
        assertTrue(wrong.score <= Verdict.INCORRECT_SCORE_CAP)
        // The token overlap really was high — this is the guard doing its job.
        assertEquals(listOf("current"), wrong.matchedKeyPoints)

        assertEquals(VerdictKind.CORRECT, checker.check(card, "the current increases").kind)
    }

    @Test
    fun addedNegationIsCappedAtIncorrect() {
        val card = textCard(
            back = "the reading does not change",
            front = "What happens to the ammeter reading when the switch closes?",
            keyPoints = listOf(KeyPoint("reading", mustInclude = true)),
        )

        val wrong = checker.check(card, "the reading changes")
        assertEquals(VerdictKind.INCORRECT, wrong.kind)
        assertIs<VerdictReason.NegationPolarity>(wrong.reason)
    }

    @Test
    fun aModelAnswerMentioningBothSidesDoesNotDisableChecking() {
        val card = textCard(
            back = "unlike a series circuit, in a parallel circuit the current splits",
            keyPoints = listOf(KeyPoint("current splits", mustInclude = true)),
        )

        assertEquals(VerdictKind.CORRECT, checker.check(card, "in a parallel circuit the current splits").kind)
    }

    @Test
    fun anAcceptedAliasRescuesAnAnswerTheGuardWouldOtherwiseReject() {
        // "no resultant force" and "the resultant force is zero" mean the same
        // thing but differ in negation polarity. Authors resolve this with an
        // alias; users resolve it with the "I was right" override.
        val withAlias = textCard(
            back = "there is no resultant force on the object",
            aliases = listOf("the resultant force is zero"),
        )
        val withoutAlias = withAlias.copy(acceptedAliases = emptyList())
        val input = "the resultant force is zero"

        assertEquals(VerdictKind.CORRECT, checker.check(withAlias, input).kind)

        val guarded = checker.check(withoutAlias, input)
        assertEquals(VerdictKind.INCORRECT, guarded.kind)
        assertIs<VerdictReason.NegationPolarity>(guarded.reason)
    }

    // ------------------------------------------ step 5: TF-IDF similarity ---

    @Test
    fun cardsWithoutKeyPointsFallBackToSimilarity() {
        val card = textCard(
            back = "the acceleration of an object is directly proportional to the resultant " +
                "force acting on it",
        )

        val partial = checker.check(card, "acceleration is directly proportional to resultant force")
        assertEquals(VerdictKind.PARTIAL, partial.kind)
        assertIs<VerdictReason.Similarity>(partial.reason)

        val nearVerbatim = checker.check(card, card.back + " always")
        assertEquals(VerdictKind.CORRECT, nearVerbatim.kind)

        val unrelated = checker.check(card, "the object slows down")
        assertEquals(VerdictKind.INCORRECT, unrelated.kind)
    }

    @Test
    fun theSimilarityFallbackUsesTheCorpusForIdf() {
        val corpus = InMemoryTermCorpus(
            listOf(
                "the force acts",
                "the mass is constant",
                "the acceleration changes",
                "centripetal force acts towards the centre",
            ),
        )
        val withCorpus = DefaultAnswerChecker(corpus = corpus)
        val card = textCard(back = "centripetal force")

        // The corpus is tiny, so this only has to stay inside the valid range and
        // still recognise the right answer.
        val verdict = withCorpus.check(card, "centripetal force")
        assertEquals(VerdictKind.CORRECT, verdict.kind)
    }

    // ------------------------------------------------------ step 6: numeric ---

    @Test
    fun numericAnswersAreCheckedAgainstTheTolerance() {
        val card = numericCard(back = "3.2 m s^-1", tolerance = 0.05)

        assertEquals(VerdictKind.CORRECT, checker.check(card, "3.2").kind)
        assertEquals(VerdictKind.CORRECT, checker.check(card, "3.2 m/s").kind)
        assertEquals(VerdictKind.CORRECT, checker.check(card, "3.3").kind)
        assertEquals(VerdictKind.PARTIAL, checker.check(card, "3.9").kind)
        assertEquals(VerdictKind.INCORRECT, checker.check(card, "9").kind)
        assertEquals(VerdictKind.INCORRECT, checker.check(card, "banana").kind)
    }

    @Test
    fun numericAnswersCanBeReadFromTheModelAnswerOrGivenExplicitly() {
        val fromBack = numericCard(back = "9.81 m/s^2")
        assertEquals(VerdictKind.CORRECT, checker.check(fromBack, "9.81").kind)
        assertEquals(VerdictKind.INCORRECT, checker.check(fromBack, "12").kind)

        val explicit = numericCard(back = "650 nm", value = 650.0, tolerance = 0.01)
        assertEquals(VerdictKind.CORRECT, checker.check(explicit, "650 nm").kind)
        assertEquals(VerdictKind.INCORRECT, checker.check(explicit, "900").kind)
    }

    @Test
    fun numericAnswersAcceptStandardForm() {
        val card = numericCard(back = "3 x 10^8 m/s", value = 3.0e8, tolerance = 0.01)

        assertEquals(VerdictKind.CORRECT, checker.check(card, "300000000").kind)
        assertEquals(VerdictKind.CORRECT, checker.check(card, "3 x 10^8").kind)
        assertEquals(VerdictKind.CORRECT, checker.check(card, "3e8").kind)
        assertEquals(VerdictKind.INCORRECT, checker.check(card, "3 x 10^6").kind)
    }

    @Test
    fun aNumericCardWithNoReadableExpectedValueAsksTheUserToJudge() {
        val card = numericCard(back = "it depends on the conditions")
        val verdict = checker.check(card, "42")

        assertTrue(verdict.requiresSelfGrade)
        assertEquals(VerdictReason.NumericNoExpectedValue, verdict.reason)
    }

    // --------------------------------------------------- step 6: expression ---

    @Test
    fun expressionsAreComparedStructurally() {
        val card = expressionCard("2*x*y")

        assertEquals(VerdictKind.CORRECT, checker.check(card, "2*x*y").kind)
        assertEquals(VerdictKind.CORRECT, checker.check(card, "y*2*x").kind)
        assertEquals(VerdictKind.CORRECT, checker.check(card, "\u00242 \\cdot y \\cdot x\u0024").kind)
    }

    @Test
    fun anUndecidableExpressionFallsBackToSelfGrading() {
        val card = expressionCard("x^2-1")

        assertEquals(VerdictKind.CORRECT, checker.check(card, "-1+x^2").kind)

        val undecidable = checker.check(card, "(x+1)(x-1)")
        assertTrue(undecidable.requiresSelfGrade)
        assertEquals(VerdictKind.PARTIAL, undecidable.kind)
        assertEquals(VerdictReason.ExpressionUndecidable, undecidable.reason)
    }

    // ---------------------------------------------------- step 6: self-grade ---

    @Test
    fun selfGradeCardsAreAlwaysHandedToTheUser() {
        val card = textCard("explain why the sky appears blue").copy(answerType = AnswerType.SELF_GRADE)

        val answered = checker.check(card, "rayleigh scattering of short wavelengths")
        assertTrue(answered.requiresSelfGrade)
        assertEquals(VerdictReason.SelfGrade, answered.reason)
        assertEquals(VerdictKind.PARTIAL, answered.kind)

        // Even a blank answer is a self-grade, not a failure: there is nothing to
        // mark automatically.
        assertTrue(checker.check(card, "").requiresSelfGrade)
    }

    // ------------------------------------------------- the optional scorer ---

    @Test
    fun aSemanticScorerCanRaiseButNeverLowerATextVerdict() {
        val card = textCard(
            back = "the current is proportional to the potential difference and inversely " +
                "proportional to the resistance",
            keyPoints = listOf(
                KeyPoint("current", mustInclude = true),
                KeyPoint("proportional to potential difference", synonyms = listOf("proportional to pd"), mustInclude = true),
                KeyPoint("inversely proportional to resistance", mustInclude = true),
            ),
        )
        val input = "current is proportional to pd"
        assertEquals(VerdictKind.PARTIAL, checker.check(card, input).kind)

        val confident = DefaultAnswerChecker(semanticScorer = FixedSemanticScorer(1.0))
        assertEquals(VerdictKind.CORRECT, confident.check(card, input).kind)

        val dismissive = DefaultAnswerChecker(semanticScorer = FixedSemanticScorer(0.0))
        assertEquals(VerdictKind.PARTIAL, dismissive.check(card, input).kind)
    }

    @Test
    fun aSemanticScorerCannotOverrideTheNegationGuard() {
        val card = textCard("the current increases", keyPoints = listOf(KeyPoint("current", mustInclude = true)))
        val confident = DefaultAnswerChecker(semanticScorer = FixedSemanticScorer(1.0))

        val verdict = confident.check(card, "the current decreases")

        assertEquals(VerdictKind.INCORRECT, verdict.kind)
        assertIs<VerdictReason.NegationConflict>(verdict.reason)
    }

    @Test
    fun theShippedSemanticScorerDeclinesToJudge() {
        assertEquals(null, NoSemanticScorer.score("model", "answer"))
    }

    private class FixedSemanticScorer(private val value: Double?) : SemanticScorer {
        override fun score(modelAnswer: String, userAnswer: String): Double? = value
    }
}
