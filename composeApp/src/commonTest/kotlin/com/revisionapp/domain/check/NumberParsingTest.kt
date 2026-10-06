package com.revisionapp.domain.check

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumberParsingTest {

    private fun assertCloseTo(expected: Double, actual: Double?, tolerance: Double = 1e-9) {
        assertTrue(actual != null, "expected a number but got null")
        assertTrue(
            abs(expected - actual) <= tolerance * maxOf(1.0, abs(expected)),
            "expected $expected but was $actual",
        )
    }

    @Test
    fun parsesPlainNumbers() {
        assertCloseTo(3.2, NumberParser.parse("3.2"))
        assertCloseTo(-4.5, NumberParser.parse("-4.5"))
        assertCloseTo(0.0, NumberParser.parse("0"))
        assertCloseTo(650.0, NumberParser.parse("650 nm"))
    }

    @Test
    fun parsesTheUnicodeMinusSign() {
        assertCloseTo(-4.5, NumberParser.parse("\u22124.5"))
    }

    @Test
    fun parsesTheFirstNumberInASentence() {
        assertCloseTo(650.0, NumberParser.parse("the wavelength is 650 nm"))
        assertCloseTo(9.81, NumberParser.parse("g = 9.81 m/s^2"))
    }

    @Test
    fun parsesStandardForm() {
        assertCloseTo(3.2e8, NumberParser.parse("3.2 x 10^8"), 1e-3)
        assertCloseTo(3.2e8, NumberParser.parse("3.2 * 10^8"), 1e-3)
        assertCloseTo(3.0e8, NumberParser.parse("\u00243 \\times 10^{8}\u0024 m/s"), 1e-3)
        assertCloseTo(1.6e-19, NumberParser.parse("1.6e-19"), 1e-24)
        assertCloseTo(2.5e-3, NumberParser.parse("2.5 \u00D7 10^-3"), 1e-8)
        assertCloseTo(100000.0, NumberParser.parse("10^5"))
        assertCloseTo(0.001, NumberParser.parse("10^-3"), 1e-9)
    }

    @Test
    fun parsesFractionsAndPercentages() {
        assertCloseTo(0.5, NumberParser.parse("1/2"))
        assertCloseTo(0.5, NumberParser.parse("50%"))
        assertCloseTo(0.25, NumberParser.parse("25 %"))
    }

    @Test
    fun ignoresThousandSeparators() {
        assertCloseTo(1200.0, NumberParser.parse("1,200"))
        assertCloseTo(1200.0, NumberParser.parse("1 200"))
    }

    @Test
    fun returnsNullWhenThereIsNoNumber() {
        assertNull(NumberParser.parse(""))
        assertNull(NumberParser.parse("   "))
        assertNull(NumberParser.parse("no number here"))
    }

    @Test
    fun unitsNeverLeakIntoTheValue() {
        assertCloseTo(3.2, NumberParser.parse("3.2 m s^-1"))
        assertCloseTo(5.0, NumberParser.parse("5 kg m^2 s^-2"))
    }

    @Test
    fun matchesInsideTolerance() {
        val outcome = NumericComparing.compare(3.2, "3.2", 0.02)
        assertIs<NumericComparing.Outcome.Match>(outcome)
        assertEquals(3.2, outcome.expected)
    }

    @Test
    fun toleranceIsRelative() {
        assertIs<NumericComparing.Outcome.Match>(NumericComparing.compare(3.2, "3.25", 0.05))
        assertIs<NumericComparing.Outcome.Match>(NumericComparing.compare(200.0, "205", 0.05))
        assertIs<NumericComparing.Outcome.Mismatch>(NumericComparing.compare(200.0, "400", 0.05))
    }

    @Test
    fun closeButOutsideToleranceIsPartial() {
        assertIs<NumericComparing.Outcome.Close>(NumericComparing.compare(3.2, "3.5", 0.02))
    }

    @Test
    fun farAwayIsAMismatch() {
        assertIs<NumericComparing.Outcome.Mismatch>(NumericComparing.compare(3.2, "9", 0.02))
    }

    @Test
    fun aZeroExpectedValueStillHasAnAbsoluteTolerance() {
        assertIs<NumericComparing.Outcome.Match>(NumericComparing.compare(0.0, "0", 0.02))
        assertIs<NumericComparing.Outcome.Match>(NumericComparing.compare(0.0, "0.01", 0.02))
        assertIs<NumericComparing.Outcome.Mismatch>(NumericComparing.compare(0.0, "1", 0.02))
    }

    @Test
    fun missingExpectedValueAndUnparseableInputAreReportedSeparately() {
        assertIs<NumericComparing.Outcome.NoExpectedValue>(NumericComparing.compare(null, "3", null))
        assertIs<NumericComparing.Outcome.Unparseable>(NumericComparing.compare(3.2, "about three", null))
    }

    @Test
    fun aMissingToleranceFallsBackToTwoPercent() {
        assertIs<NumericComparing.Outcome.Match>(NumericComparing.compare(100.0, "101", null))
        assertIs<NumericComparing.Outcome.Mismatch>(NumericComparing.compare(100.0, "150", null))
    }
}
