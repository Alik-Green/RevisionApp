package com.revisionapp.domain.check

import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.KeyPoint
import com.revisionapp.domain.model.TopicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * The two ways the checker gets better at marking a particular card: remembering
 * an answer the user vouched for, and rescuing a paraphrase that misses every key
 * point but says nearly what the model answer says.
 */
class LearnedAnswersTest {

    private val checker = DefaultAnswerChecker()

    private fun card(
        id: String,
        back: String,
        keyPoints: List<KeyPoint>,
        aliases: List<String> = emptyList(),
    ): Card = Card(
        id = CardId(id),
        topicId = TopicId("topic"),
        front = "question",
        back = back,
        keyPoints = keyPoints,
        acceptedAliases = aliases,
    )

    /** Key points demand the symbolic form, so a correct answer in words misses them. */
    private val wordedCard = card(
        id = "w",
        back = "The rate of change of momentum.",
        keyPoints = listOf(KeyPoint("F = dp/dt", mustInclude = true)),
    )

    private val unitCard = card(
        id = "u",
        back = "The newton, N",
        keyPoints = listOf(
            KeyPoint("newton", mustInclude = true),
            KeyPoint("kg m/s^2", mustInclude = true),
        ),
    )

    // ------------------------------------------------------ learned answers ---

    @Test
    fun anAnswerTheUserVouchedForIsCorrectNextTime() {
        val before = checker.check(unitCard, "the force unit")
        assertNotEquals(VerdictKind.CORRECT, before.kind)

        val after = checker.check(unitCard, "the force unit", listOf("the force unit"))

        assertEquals(VerdictKind.CORRECT, after.kind)
        assertIs<VerdictReason.LearnedMatch>(after.reason)
    }

    @Test
    fun aLearnedAnswerIsMatchedAfterNormalisation() {
        // Punctuation, case and spacing must not stop a vouched-for answer matching.
        val verdict = checker.check(unitCard, "The  Force   UNIT.", listOf("the force unit"))

        assertEquals(VerdictKind.CORRECT, verdict.kind)
    }

    @Test
    fun aLearnedAnswerOnlyAppliesToTheWordingThatWasVouchedFor() {
        val verdict = checker.check(unitCard, "something else entirely", listOf("the force unit"))

        assertNotEquals(VerdictKind.CORRECT, verdict.kind)
    }

    @Test
    fun anAuthoredAliasStillReportsItselfRatherThanALearnedMatch() {
        val withAlias = unitCard.copy(acceptedAliases = listOf("the force unit"))

        val verdict = checker.check(withAlias, "the force unit", listOf("the force unit"))

        assertIs<VerdictReason.AliasMatch>(verdict.reason)
    }

    @Test
    fun noLearnedAnswersChangesNothing() {
        assertEquals(
            checker.check(unitCard, "the force unit"),
            checker.check(unitCard, "the force unit", emptyList()),
        )
    }

    // ------------------------------------------------------- paraphrase rescue ---

    @Test
    fun aCloseParaphraseIsRescuedFromIncorrectToPartial() {
        // The only key point is symbolic, so this correct answer in words misses it
        // entirely and coverage alone says INCORRECT.
        val verdict = checker.check(wordedCard, "the rate of change of momentum with time")

        assertEquals(VerdictKind.PARTIAL, verdict.kind)
        assertIs<VerdictReason.Similarity>(verdict.reason)
    }

    @Test
    fun aRescueNeverReachesCorrect() {
        // Key points still decide CORRECT. A paraphrase is evidence the answer is
        // probably right, not evidence that it is.
        val verdict = checker.check(wordedCard, "the rate of change of momentum with time")

        assertNotEquals(VerdictKind.CORRECT, verdict.kind)
    }

    @Test
    fun anUnrelatedAnswerIsNotRescued() {
        val verdict = checker.check(wordedCard, "it is always zero")

        assertEquals(VerdictKind.INCORRECT, verdict.kind)
    }

    @Test
    fun aRescueDoesNotWeakenACardThatAlreadyMatched() {
        val exact = checker.check(wordedCard, "F = dp/dt")

        assertEquals(VerdictKind.CORRECT, exact.kind)
    }

    @Test
    fun theNegationGuardStillCapsARescuedAnswer() {
        // Similar wording, opposite meaning: the guard has the last word, so the
        // rescue cannot turn a contradiction into credit.
        val contradicting = card(
            id = "n",
            back = "The current does not stop when the switch opens.",
            keyPoints = listOf(KeyPoint("does not stop", mustInclude = true)),
        )

        val verdict = checker.check(contradicting, "the current stops when the switch opens")

        assertEquals(VerdictKind.INCORRECT, verdict.kind)
    }
}
