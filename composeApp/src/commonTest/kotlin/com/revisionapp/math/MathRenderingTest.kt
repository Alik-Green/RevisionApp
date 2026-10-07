package com.revisionapp.math

import com.revisionapp.ui.render.UnicodeRichTextRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two renderers behind [com.revisionapp.ui.render.RichTextRenderer].
 *
 * `render` is the display form (real Unicode scripts, `(a)/(b)` fractions) and
 * `canonical` is the ASCII form the answer checker compares. They share one
 * parser, which is the whole point: display and comparison cannot drift apart
 * again.
 */
class MathRenderingTest {

    private val renderer = UnicodeRichTextRenderer()

    /** Wraps [body] in the `$...$` delimiters the content packs use. */
    private fun maths(body: String): String = "$" + body + "$"

    // ------------------------------------------------- scripts ---

    @Test
    fun bareSuperscriptsBecomeRealSuperscripts() {
        // The bug this whole rewrite exists for: an unbraced ^ used to survive
        // untouched, so cards showed "x^2" instead of "x²".
        assertEquals("x²", renderer.render("x^2"))
        assertEquals("m/s²", renderer.render("m/s^2"))
        assertEquals("O(n²)", renderer.render("O(n^2)"))
        assertEquals("10⁻¹⁹", renderer.render("10^-19"))
        assertEquals("3.2 m s⁻¹", renderer.render("3.2 m s^-1"))
    }

    @Test
    fun bareSubscriptsBecomeRealSubscripts() {
        assertEquals("log₃", renderer.render("log_3"))
        assertEquals("xₙ₊₁", renderer.render("x_{n+1}"))
        assertEquals("aᵢ", renderer.render("a_i"))
        assertEquals("H₂O", renderer.render("H_2O"))
    }

    @Test
    fun bracedAndBareScriptsAgree() {
        assertEquals(renderer.render("x^2"), renderer.render("x^{2}"))
        assertEquals(renderer.render("x_3"), renderer.render("x_{3}"))
        assertEquals("xⁿ⁺¹", renderer.render("x^{n+1}"))
        assertEquals("xⁿ⁻¹", renderer.render("x^{n-1}"))
    }

    @Test
    fun aScriptWithNoUnicodeEquivalentUsesCaretNotation() {
        // 'b', 'c' and 'k' have no superscript characters, so the whole script
        // falls back rather than mixing one real superscript with literal letters.
        assertEquals("x^abc", renderer.render("x^{abc}"))
        assertEquals("x^2k", renderer.render("x^{2k}"))
        assertEquals("x^(n*k)", renderer.render("x^{n*k}"))
    }

    @Test
    fun anUnbracedScriptTakesASignAndItsDigits() {
        // Strict LaTeX would read 10^-19 as a superscript minus followed by 19.
        // Hand-authored revision content means 10 to the power of minus 19, so
        // the parser reads the sign and the digits together, while x^2y stays
        // x squared times y.
        assertEquals("10⁻¹⁹", renderer.render("10^-19"))
        assertEquals("x²y", renderer.render("x^2y"))
        assertEquals("xⁿ", renderer.render("x^n"))
    }

    @Test
    fun aScriptNeedsBracketsOnlyWhenItContainsAnOperator() {
        assertEquals("s^-1", renderer.canonical("s^{-1}"))
        assertEquals("x^(n+1)", renderer.canonical("x^{n+1}"))
        assertEquals("x^2", renderer.canonical("x^{2}"))
    }

    // ------------------------------------------------- symbols ---

    @Test
    fun arrowsBecomeArrowSymbols() {
        // The other reported bug: \Rightarrow was missing from the symbol table
        // and fell through to "print the command name".
        assertEquals("⇒", renderer.render("\\Rightarrow"))
        assertEquals("→", renderer.render("\\rightarrow"))
        assertEquals("⇔", renderer.render("\\Leftrightarrow"))
        assertEquals("⟹", renderer.render("\\implies"))
        assertEquals("←", renderer.render("\\leftarrow"))
        assertEquals("↦", renderer.render("\\mapsto"))
    }

    @Test
    fun relationsAndOperatorsBecomeSymbols() {
        assertEquals("≤", renderer.render("\\leq"))
        assertEquals("≤", renderer.render("\\le"))
        assertEquals("≥", renderer.render("\\geq"))
        assertEquals("≠", renderer.render("\\neq"))
        assertEquals("≈", renderer.render("\\approx"))
        assertEquals("×", renderer.render("\\times"))
        assertEquals("·", renderer.render("\\cdot"))
        assertEquals("±", renderer.render("\\pm"))
        assertEquals("∞", renderer.render("\\infty"))
        assertEquals("∑", renderer.render("\\sum"))
        assertEquals("∫", renderer.render("\\int"))
        assertEquals("∈", renderer.render("\\in"))
    }

    @Test
    fun logicSymbolsBecomeSymbols() {
        assertEquals("∀", renderer.render("\\forall"))
        assertEquals("∃", renderer.render("\\exists"))
        assertEquals("¬", renderer.render("\\neg"))
        assertEquals("∧", renderer.render("\\land"))
        assertEquals("∨", renderer.render("\\lor"))
        assertEquals("∴", renderer.render("\\therefore"))
    }

    @Test
    fun greekLettersBecomeLetters() {
        assertEquals("α β γ", renderer.render("\\alpha \\beta \\gamma"))
        assertEquals("θ", renderer.render("\\theta"))
        assertEquals("λ", renderer.render("\\lambda"))
        assertEquals("ω", renderer.render("\\omega"))
        assertEquals("Δ", renderer.render("\\Delta"))
        assertEquals("π", renderer.render("\\pi"))
        assertEquals("Ω", renderer.render("\\Omega"))
    }

    @Test
    fun aLongerCommandIsNotEatenByAShorterOne() {
        // Substring replacement needed \leq listed before \le. A parsed name has
        // no such ordering hazard.
        assertEquals("≤", renderer.render("\\leq"))
        assertEquals("≠", renderer.render("\\neq"))
        assertEquals("∉", renderer.render("\\notin"))
        assertEquals("∞", renderer.render("\\infty"))
    }

    // ---------------------------------------------- structure ---

    @Test
    fun fractionsBecomeInlinePairsInPlainText() {
        assertEquals("(a)/(b)", renderer.render(maths("\\frac{a}{b}")))
        assertEquals("(a)/(b)", renderer.render(maths("\\dfrac{a}{b}")))
        assertEquals("(x²)/(2)", renderer.render(maths("\\frac{x^{2}}{2}")))
        assertEquals("(n(n + 1))/(2)", renderer.render(maths("\\frac{n(n + 1)}{2}")))
    }

    @Test
    fun nestedBracesAreHandled() {
        assertEquals("√((1)/(2))", renderer.render(maths("\\sqrt{\\frac{1}{2}}")))
        assertEquals("xⁿ⁺¹", renderer.render(maths("x^{n+1}")))
        assertEquals("(x²)/(y²)", renderer.render(maths("\\frac{x^2}{y^2}")))
    }

    @Test
    fun rootsUseTheRightRadical() {
        assertEquals("√2", renderer.render(maths("\\sqrt{2}")))
        assertEquals("∛x", renderer.render(maths("\\sqrt[3]{x}")))
        assertEquals("sqrt(2)", renderer.canonical(maths("\\sqrt{2}")))
    }

    @Test
    fun textCommandsKeepTheirContents() {
        assertEquals("if x > 0", renderer.render(maths("\\text{if} x > 0")))
        assertEquals("m/s", renderer.render(maths("\\mathrm{m/s}")))
    }

    // ------------------------------------------- degradation ---

    @Test
    fun anUnknownCommandShowsItsNameAndNeverABackslash() {
        assertEquals("frobnicate", renderer.render("\\frobnicate"))
        assertEquals("abc", renderer.render("\\abc"))
        assertEquals("abcx", renderer.render("\\abc{x}"))
        assertFalse(renderer.render("\\someNewCommand{y}").contains("\\"))
        assertTrue(renderer.render("\\someNewCommand{y}").contains("someNewCommand"))
    }

    @Test
    fun malformedInputNeverThrowsAndNeverShowsABackslash() {
        val malformed = listOf(
            "\\frac{a",
            "\\frac",
            "\\sqrt[3",
            "^",
            "_",
            "x^{}",
            "{",
            "}",
            "{{{",
            "\\",
            "$$$",
            "\\unknown\\another{x",
            "^_^_",
            "a{b}c}d{e",
        )
        for (source in malformed) {
            val rendered = renderer.render(source)
            val canonical = renderer.canonical(source)
            assertFalse(rendered.contains("\\"), "rendered $source to $rendered")
            assertFalse(canonical.contains("\\"), "canonicalised $source to $canonical")
        }
    }

    @Test
    fun prosePassesThroughUnchanged() {
        val prose = "The centripetal force points towards the centre of the circle."
        assertEquals(prose, renderer.render(prose))
        assertFalse(renderer.hasMaths(prose))
    }

    @Test
    fun hasMathsSpotsDelimitedAndBareMaths() {
        assertTrue(renderer.hasMaths(maths("\\alpha")))
        assertTrue(renderer.hasMaths("9.81 m/s^2"))
        assertTrue(renderer.hasMaths("log_3 x"))
        assertTrue(renderer.hasMaths("θ"))
        assertFalse(renderer.hasMaths("no maths here"))
    }

    // ------------------------------------- the checker's form ---

    @Test
    fun everyWayOfTypingAPowerCanonicalisesTheSame() {
        val canonical = renderer.canonical("x^2")
        assertEquals("x^2", canonical)
        assertEquals(canonical, renderer.canonical("x²"))
        assertEquals(canonical, renderer.canonical("x**2"))
        assertEquals(canonical, renderer.canonical(maths("x^{2}")))
    }

    @Test
    fun typedShortcutsCanonicaliseToWhatLatexProduces() {
        assertEquals("=>", renderer.canonical("\\Rightarrow"))
        assertEquals("=>", renderer.canonical("=>"))
        assertEquals("->", renderer.canonical("\\rightarrow"))
        assertEquals("->", renderer.canonical("->"))
        assertEquals("<=", renderer.canonical("\\leq"))
        assertEquals("<=", renderer.canonical("<="))
        assertEquals(">=", renderer.canonical("\\geq"))
        assertEquals(">=", renderer.canonical(">="))
        assertEquals("+/-", renderer.canonical("\\pm"))
        assertEquals("+/-", renderer.canonical("+-"))
    }

    @Test
    fun subscriptedSequencesCanonicaliseToUnderscoreForm() {
        assertEquals("x_(n+1)", renderer.canonical("x_{n+1}"))
        assertEquals("x_(n+1)", renderer.canonical("xₙ₊₁"))
        assertEquals("10^-19", renderer.canonical("10⁻¹⁹"))
    }

    @Test
    fun theCanonicalFormKeepsStructureForTheExpressionComparer() {
        assertEquals("(x^2)/(2)", renderer.canonical(maths("\\frac{x^{2}}{2}")))
        assertEquals("3 * 10^8", renderer.canonical(maths("3 \\times 10^{8}")))
        assertEquals("2 pi r", renderer.canonical(maths("2\\pi r")))
    }

    @Test
    fun parsingIsStableAcrossRepeatedCalls() {
        val source = maths("\\frac{x^{2} + 1}{\\sqrt{y_1}}")
        assertEquals(renderer.render(source), renderer.render(source))
        assertEquals(renderer.canonical(source), renderer.canonical(source))
        assertTrue(renderer.parse(source).isNotEmpty())
    }

    @Test
    fun anEmptySourceStaysEmpty() {
        assertEquals("", renderer.render(""))
        assertEquals("", renderer.canonical(""))
        assertEquals(MathNode.Empty, renderer.parse(""))
    }
}
