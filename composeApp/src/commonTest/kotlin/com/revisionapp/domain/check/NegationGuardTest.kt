package com.revisionapp.domain.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The guard is the part of the checker most likely to be wrong in either
 * direction, so it gets its own suite: false negatives let a contradictory
 * answer score as correct, false positives annoy the user (who can override).
 */
class NegationGuardTest {

    private fun conflict(model: String, answer: String) = NegationGuard.findConflict(
        modelTokens = TextNormaliser.matchTokenSet(model),
        answerTokens = TextNormaliser.matchTokenSet(answer),
    )

    @Test
    fun detectsIncreaseVersusDecrease() {
        val result = conflict("the current increases", "the current decreases")
        assertIs<NegationGuard.Conflict.OppositeTerm>(result)
        assertEquals("increas", result.modelTerm)
        assertEquals("decreas", result.answerTerm)
    }

    @Test
    fun detectsTheReverseDirectionToo() {
        assertIs<NegationGuard.Conflict.OppositeTerm>(conflict("the resistance decreases", "it increases"))
    }

    @Test
    fun detectsPerpendicularVersusParallel() {
        val result = conflict("the force is perpendicular to the velocity", "parallel")
        assertIs<NegationGuard.Conflict.OppositeTerm>(result)
        assertEquals("parallel", result.answerTerm)
    }

    @Test
    fun detectsSeriesVersusParallel() {
        val result = conflict("the components are in series", "the components are in parallel")
        assertIs<NegationGuard.Conflict.OppositeTerm>(result)
        assertEquals("seri", result.modelTerm)
        assertEquals("parallel", result.answerTerm)
    }

    @Test
    fun detectsComparisonWords() {
        assertIs<NegationGuard.Conflict.OppositeTerm>(conflict("the reading is greater", "the reading is less"))
        assertIs<NegationGuard.Conflict.OppositeTerm>(conflict("the wavelength is longer", "it is shorter"))
        assertIs<NegationGuard.Conflict.OppositeTerm>(conflict("the image is upright", "the image is inverted"))
        assertIs<NegationGuard.Conflict.OppositeTerm>(conflict("the wire absorbs energy", "the wire emits energy"))
    }

    @Test
    fun detectsTrueVersusFalse() {
        assertIs<NegationGuard.Conflict.OppositeTerm>(conflict("the statement is true", "the statement is false"))
    }

    @Test
    fun aModelContainingBothSidesCarriesNoSignal() {
        // "Unlike a series circuit, in a parallel circuit ..." mentions both, so
        // an answer that says "parallel" is not contradicting anything.
        assertNull(
            conflict(
                "unlike a series circuit, in a parallel circuit the current splits",
                "in a parallel circuit the current splits",
            ),
        )
    }

    @Test
    fun agreeingAnswersProduceNoConflict() {
        assertNull(conflict("the current increases", "the current increases"))
        assertNull(conflict("the current increases", "it goes up"))
        assertNull(conflict("the force is perpendicular to the velocity", "perpendicular to the velocity"))
    }

    @Test
    fun detectsAddedNegation() {
        val result = conflict("the reading does not change", "the reading changes")
        assertIs<NegationGuard.Conflict.Polarity>(result)
        assertTrue(result.modelNegated)
        assertFalse(result.answerNegated)
    }

    @Test
    fun detectsRemovedNegation() {
        val result = conflict("there is no resultant force on the object", "the resultant force is zero")
        assertIs<NegationGuard.Conflict.Polarity>(result)
    }

    @Test
    fun contractionsCountAsNegations() {
        assertTrue(NegationGuard.isNegated(TextNormaliser.matchTokenSet("doesn't change")))
        assertTrue(NegationGuard.isNegated(TextNormaliser.matchTokenSet("it won't reach terminal velocity")))
        assertTrue(NegationGuard.isNegated(TextNormaliser.matchTokenSet("without friction")))
        assertFalse(NegationGuard.isNegated(TextNormaliser.matchTokenSet("it changes")))
        // "note" and "nothing" both start with 'n' but only one is a negation.
        assertFalse(NegationGuard.isNegation(Stemmer.stem("note")))
        assertTrue(NegationGuard.isNegation(Stemmer.stem("nothing")))
    }

    @Test
    fun doubleNegationAgreesAndProducesNoConflict() {
        assertNull(conflict("the current does not stop", "the current doesn't stop"))
    }

    @Test
    fun anUnrelatedAnswerProducesNoOppositeTermConflict() {
        assertNull(conflict("the current increases", "banana"))
    }
}
