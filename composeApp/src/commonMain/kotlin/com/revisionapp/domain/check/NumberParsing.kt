package com.revisionapp.domain.check

import kotlin.math.abs
import kotlin.math.pow

/**
 * Pulls a number out of free text. Handles the forms exam answers actually take:
 * `3.2`, `-4.5`, `1 200`, `5,200`, `1/2`, `50%`, `3.2 x 10^8`, `3.2 \times 10^{8}`,
 * `1.6e-19`, `650 nm`, `the wavelength is 650 nm`.
 *
 * Units are ignored: everything after the first complete number is dropped.
 */
object NumberParser {

    private val POWER_OF_TEN = Regex("""([-+]?[0-9]*\.?[0-9]+)\s*[x*]?\s*10\s*\^\s*\(?([-+]?[0-9]+)\)?""")
    private val BARE_POWER_OF_TEN = Regex("""^10\s*\^\s*\(?([-+]?[0-9]+)\)?$""")
    private val EXPONENT_NOTATION = Regex("""([-+]?[0-9]*\.?[0-9]+)\s*e\s*([-+]?[0-9]+)""")
    private val FRACTION = Regex("""([-+]?[0-9]*\.?[0-9]+)\s*/\s*([0-9]*\.?[0-9]+)""")
    private val PLAIN_NUMBER = Regex("""[-+]?[0-9]*\.?[0-9]+""")
    private val DIGIT_GAP = Regex("""(\d)\s+(\d)""")

    /** The number in [raw], or null when there is nothing numeric to read. */
    fun parse(raw: String): Double? {
        val cleaned = TextNormaliser.toPlainText(raw)
            .lowercase()
            .replace(",", "")
            // SI-style thousands separators: "1 200" is one number, not two.
            .replace(DIGIT_GAP, "\$1\$2")
            .trim()
        if (cleaned.isEmpty()) return null

        val magnitude = parseMagnitude(cleaned) ?: return null
        return if ('%' in cleaned) magnitude / PERCENT_DIVISOR else magnitude
    }

    private fun parseMagnitude(text: String): Double? {
        BARE_POWER_OF_TEN.find(text)?.let { match ->
            val exponent = match.groupValues[1].toIntOrNull() ?: return@let
            return 10.0.pow(exponent)
        }
        POWER_OF_TEN.find(text)?.let { match ->
            val mantissa = match.groupValues[1].toDoubleOrNull() ?: return@let
            val exponent = match.groupValues[2].toIntOrNull() ?: return@let
            return mantissa * 10.0.pow(exponent)
        }
        EXPONENT_NOTATION.find(text)?.let { match ->
            val mantissa = match.groupValues[1].toDoubleOrNull() ?: return@let
            val exponent = match.groupValues[2].toIntOrNull() ?: return@let
            return mantissa * 10.0.pow(exponent)
        }
        FRACTION.find(text)?.let { match ->
            val numerator = match.groupValues[1].toDoubleOrNull() ?: return@let
            val denominator = match.groupValues[2].toDoubleOrNull() ?: return@let
            if (denominator != 0.0) return numerator / denominator
        }
        PLAIN_NUMBER.find(text)?.let { match ->
            return match.value.toDoubleOrNull()
        }
        return null
    }

    private const val PERCENT_DIVISOR = 100.0
}

/**
 * Compares a typed number against the expected one, honouring the card's
 * tolerance. Tolerance is relative (`0.05` means +/- 5%) and defaults to
 * [com.revisionapp.domain.model.NumericSpec.DEFAULT_TOLERANCE]; for an expected
 * value of zero the same number is used as an absolute tolerance, because 5% of
 * zero would accept only exactly zero.
 */
object NumericComparing {

    sealed interface Outcome {
        data class Match(val expected: Double, val actual: Double, val allowedError: Double) : Outcome
        data class Close(val expected: Double, val actual: Double, val allowedError: Double) : Outcome
        data class Mismatch(val expected: Double, val actual: Double, val allowedError: Double) : Outcome
        data class Unparseable(val input: String) : Outcome
        data object NoExpectedValue : Outcome
    }

    /** How many times the tolerance still counts as "close" rather than wrong. */
    private const val CLOSE_FACTOR = 10.0

    fun compare(expected: Double?, actualText: String, relativeTolerance: Double?): Outcome {
        val target = expected ?: return Outcome.NoExpectedValue
        val actual = NumberParser.parse(actualText) ?: return Outcome.Unparseable(actualText)

        val tolerance = (relativeTolerance ?: DEFAULT_RELATIVE_TOLERANCE).let { if (it < 0.0) 0.0 else it }
        val allowedError = if (target == 0.0) maxOf(tolerance, MINIMUM_ABSOLUTE) else abs(target) * tolerance
        val error = abs(actual - target)

        return when {
            error <= allowedError -> Outcome.Match(target, actual, allowedError)
            error <= allowedError * CLOSE_FACTOR -> Outcome.Close(target, actual, allowedError)
            else -> Outcome.Mismatch(target, actual, allowedError)
        }
    }

    private const val DEFAULT_RELATIVE_TOLERANCE = 0.02
    private const val MINIMUM_ABSOLUTE = 1e-9
}
