package com.revisionapp.ui.render

import com.revisionapp.math.MathNode
import com.revisionapp.math.MathParser
import com.revisionapp.math.MathPlainText
import com.revisionapp.math.ScriptStyle

/**
 * The seam between "content contains maths" and "here is something that can draw
 * it".
 *
 * Two renderers sit behind it, as the brief requires:
 *
 * - the **fallback** ([render]), a plain `String` with real Unicode superscripts,
 *   subscripts and symbols and `(a)/(b)` fractions. It is what search snippets,
 *   the answer checker and any non-layout context use, and it is the safety net
 *   the Compose renderer falls back to if layout throws.
 * - the **main** renderer, which is the parsed form ([parse]) laid out by
 *   `MathText` in Compose: stacked fractions, radicals with a vinculum, and
 *   baseline-shifted scripts where Unicode has no equivalent.
 *
 * Screens never touch raw LaTeX. See docs/DECISIONS.md D10 and D32.
 */
interface RichTextRenderer {

    /** [source] as displayable text. Prose passes through unchanged. */
    fun render(source: String): String

    /**
     * [source] in the canonical ASCII form the answer checker compares, so that
     * `x^2` from LaTeX, `x²` typed directly and `x**2` typed as a shortcut are
     * all the same string.
     */
    fun canonical(source: String): String

    /** True when [source] contains maths that needs laying out. */
    fun hasMaths(source: String): Boolean

    /** The parsed tree, for the Compose renderer. */
    fun parse(source: String): List<MathNode>
}

/**
 * The shipped renderer: pure Kotlin, no dependencies, identical on desktop and
 * Android. Parsing is stateless on purpose — a shared cache would need
 * synchronisation `commonMain` cannot provide, and `MathText` caches per
 * composition slot with `remember(source)`, which is where the reuse is.
 */
class UnicodeRichTextRenderer : RichTextRenderer {

    override fun render(source: String): String =
        if (source.isEmpty()) source else MathPlainText.render(parse(source), ScriptStyle.UNICODE)

    override fun canonical(source: String): String =
        if (source.isEmpty()) source else MathPlainText.render(parse(source), ScriptStyle.ASCII)

    override fun hasMaths(source: String): Boolean = source.any { character ->
        character == MATH_DELIMITER ||
            character == '\\' ||
            character == '^' ||
            character == '_' ||
            character.code > GREEK_AND_SYMBOLS_START
    }

    override fun parse(source: String): List<MathNode> = MathParser.parse(source)

    private companion object {
        const val MATH_DELIMITER: Char = '\u0024'

        /** Anything above the Latin blocks: Greek, operators, scripts, arrows. */
        const val GREEK_AND_SYMBOLS_START: Int = 0x0370
    }
}
