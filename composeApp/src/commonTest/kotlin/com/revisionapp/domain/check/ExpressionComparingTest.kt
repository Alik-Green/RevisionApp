package com.revisionapp.domain.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpressionComparingTest {

    @Test
    fun multiplicationIsCommutative() {
        assertTrue(ExpressionComparing.equivalent("2*x*y", "y*2*x"))
        assertTrue(ExpressionComparing.equivalent("m*g*h", "g*m*h"))
        assertEquals("2*x*y", ExpressionComparing.canonicalise("y*2*x"))
    }

    @Test
    fun subtractionIsNormalisedToAdditionOfANegative() {
        assertTrue(ExpressionComparing.equivalent("a-b", "-b+a"))
        assertTrue(ExpressionComparing.equivalent("x^2-1", "-1+x^2"))
    }

    @Test
    fun latexFractionsMatchPlainDivision() {
        assertTrue(ExpressionComparing.equivalent("\\frac{a}{b}", "a/b"))
        assertTrue(ExpressionComparing.equivalent("\u0024\\frac{1}{2}\u0024", "1/2"))
    }

    @Test
    fun superscriptBracesAreOptional() {
        assertTrue(ExpressionComparing.equivalent("x^{2}", "x^2"))
        assertTrue(ExpressionComparing.equivalent("m*s^{-1}", "m/s"))
    }

    @Test
    fun bracketsThatChangeMeaningAreKept() {
        assertTrue(ExpressionComparing.equivalent("(a+b)*c", "c*(a+b)"))
        assertFalse(ExpressionComparing.equivalent("(a+b)*c", "a+b*c"))
    }

    @Test
    fun expansionIsOutOfScopeSoItReportsNotEquivalent() {
        // The caller turns this into a self-grade rather than marking it wrong.
        assertFalse(ExpressionComparing.equivalent("(x+1)(x-1)", "x^2-1"))
    }

    @Test
    fun implicitMultiplicationIsNotInferred() {
        assertFalse(ExpressionComparing.equivalent("2xy", "2*x*y"))
    }

    @Test
    fun differentExpressionsAreNotEquivalent() {
        assertFalse(ExpressionComparing.equivalent("x+1", "x+2"))
        assertFalse(ExpressionComparing.equivalent("v/t", "v*t"))
    }

    @Test
    fun emptyInputCanonicalisesToEmptyAndNeverMatches() {
        assertEquals("", ExpressionComparing.canonicalise(""))
        assertEquals("", ExpressionComparing.canonicalise("   "))
        assertFalse(ExpressionComparing.equivalent("", ""))
        assertFalse(ExpressionComparing.equivalent("", "x"))
    }

    @Test
    fun whitespaceAndThousandsSeparatorsAreIgnored() {
        assertTrue(ExpressionComparing.equivalent("2 * x", "2*x"))
        assertTrue(ExpressionComparing.equivalent("1,000*x", "1000*x"))
    }
}
