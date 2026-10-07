package com.revisionapp.domain.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TextNormalisingTest {

    /** Wraps [body] in the `$...$` delimiters the content packs use. */
    private fun maths(body: String): String = "\u0024$body\u0024"

    @Test
    fun latexFractionsBecomeReadableText() {
        assertEquals("(a)/(b)", TextNormaliser.toPlainText(maths("\\frac{a}{b}")))
        assertEquals("(a)/(b)", TextNormaliser.toPlainText(maths("\\dfrac{a}{b}")))
        assertEquals("(x^2)/(2)", TextNormaliser.toPlainText(maths("\\frac{x^{2}}{2}")))
    }

    @Test
    fun latexCommandsBecomeWords() {
        assertEquals("theta", TextNormaliser.toPlainText(maths("\\theta")))
        assertEquals("v = sqrt(2)", TextNormaliser.toPlainText(maths("v = \\sqrt{2}")))
        assertEquals("3 * 10^8", TextNormaliser.toPlainText(maths("3 \\times 10^{8}")))
        assertEquals("2 pi r", TextNormaliser.toPlainText(maths("2\\pi r")))
        assertEquals("a <= b", TextNormaliser.toPlainText(maths("a \\leq b")))
        // Longest command first, so \leq is not eaten by \le.
        assertEquals("a <= b", TextNormaliser.toPlainText(maths("a \\le b")))
    }

    @Test
    fun superscriptsKeepTheirMeaning() {
        assertEquals("s^-1", TextNormaliser.toPlainText("s\u0024^{-1}\u0024"))
        assertEquals("x^2", TextNormaliser.toPlainText(maths("x^{2}")))
        assertEquals("x^(n+1)", TextNormaliser.toPlainText(maths("x^{n+1}")))
    }

    @Test
    fun unicodeMathsSymbolsAreMapped() {
        assertEquals("5 * 10^3 m", TextNormaliser.toPlainText("5 \u00D7 10\u00B3 m"))
        assertEquals("a <= b", TextNormaliser.toPlainText("a \u2264 b"))
        assertEquals("theta", TextNormaliser.toPlainText("\u03B8"))
        assertEquals("ohm", TextNormaliser.toPlainText("\u03A9"))
    }

    @Test
    fun normalisationIsCaseAndPunctuationInsensitive() {
        assertEquals("doesnt", TextNormaliser.normalise("Doesn't"))
        assertEquals("force mass acceleration", TextNormaliser.normalise("Force, mass & acceleration!"))
        assertEquals("well known effect", TextNormaliser.normalise("well-known effect"))
    }

    @Test
    fun aMathsPrimeIsKeptWhileAContractionApostropheIsDropped() {
        // "Doesn't" must stay "doesnt" so the negation guard still sees it, but
        // f'(a) is a different function from f(a) and must not collapse into it.
        assertEquals("doesnt", TextNormaliser.normalise("Doesn't"))
        assertNotEquals(TextNormaliser.normalise("f(a)"), TextNormaliser.normalise("f'(a)"))
        assertNotEquals(TextNormaliser.normalise("a"), TextNormaliser.normalise("-a"))
    }

    @Test
    fun numbersSurviveNormalisation() {
        assertEquals("-4.5", TextNormaliser.normalise("-4.5"))
        assertEquals("3.2 m s^-1", TextNormaliser.normalise("3.2 m s^-1"))
        assertEquals("5-3", TextNormaliser.normalise("5-3"))
    }

    @Test
    fun tokenisationTrimsSentencePunctuation() {
        assertEquals(listOf("it", "moves"), TextNormaliser.tokens("It moves."))
        assertEquals(listOf("3.2", "m", "s^-1"), TextNormaliser.tokens("3.2 m s^-1."))
    }

    @Test
    fun numberWordsBecomeDigits() {
        assertEquals(listOf("1", "complet", "rotation"), TextNormaliser.matchTokens("one complete rotation"))
        assertEquals(listOf("20"), TextNormaliser.matchTokens("twenty"))
    }

    @Test
    fun everyInflectionOfIncreaseStemsTheSame() {
        val stems = listOf("increase", "increases", "increased", "increasing").map { Stemmer.stem(it) }
        assertEquals(setOf("increas"), stems.toSet())
    }

    @Test
    fun increaseAndDecreaseStayDistinctAfterStemming() {
        assertEquals("increas", Stemmer.stem("increase"))
        assertEquals("decreas", Stemmer.stem("decrease"))
        assertFalse(Stemmer.stem("increase") == Stemmer.stem("decrease"))
    }

    @Test
    fun seriesIsStableAndDistinctFromParallel() {
        // "series" is a real word in circuit questions and must not be mangled
        // into something that could collide with another term.
        assertEquals(Stemmer.stem("series"), Stemmer.stem("in series".split(" ").last()))
        assertFalse(Stemmer.stem("series") == Stemmer.stem("parallel"))
    }

    @Test
    fun energiesAndEnergyStillMatchThroughFuzzyComparison() {
        assertTrue(EditDistance.isClose(Stemmer.stem("energy"), Stemmer.stem("energies")))
    }

    @Test
    fun editDistanceHandlesTyposButNotOpposites() {
        assertEquals(3, EditDistance.between("kitten", "sitting"))
        assertEquals(0, EditDistance.between("same", "same"))
        assertTrue(EditDistance.isClose("velocity", "veloctiy"))
        assertTrue(EditDistance.isClose("centripetal", "centripetel"))
        // Two edits on a seven-letter token is more than the threshold allows,
        // which is exactly what keeps increase/decrease apart.
        assertFalse(EditDistance.isClose("increas", "decreas"))
        // Short tokens must match exactly: "mass" and "pass" are different physics.
        assertFalse(EditDistance.isClose("mass", "pass"))
        assertFalse(EditDistance.isClose("not", "now"))
    }

    @Test
    fun editDistanceThresholdsGrowWithLength() {
        assertEquals(0, EditDistance.thresholdFor(4))
        assertEquals(1, EditDistance.thresholdFor(5))
        assertEquals(1, EditDistance.thresholdFor(7))
        assertEquals(2, EditDistance.thresholdFor(8))
    }
}
