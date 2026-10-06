package com.revisionapp.ui.render

/**
 * The seam between "content contains maths" and "here is a widget that draws it".
 *
 * Cards store LaTeX inside `$...$`. Screens never touch that text directly: they
 * call [render] and display the result, so a real typesetting backend (Skia, a
 * bundled math font, a WebView) can replace the shipped implementation without a
 * single screen changing. See docs/DECISIONS.md D10.
 */
interface RichTextRenderer {
    /** [source] as displayable text. Non-maths text passes through unchanged. */
    fun render(source: String): String

    /** True when [source] contains maths this renderer had to approximate. */
    fun hasMaths(source: String): Boolean
}

/**
 * Renders LaTeX to Unicode: real superscripts and subscripts where they exist,
 * Greek letters, and maths operators. No fractions bars or integrals — that needs
 * a typesetter — but `x^{2}`, `\theta`, `\frac{a}{b}` and `3 \times 10^{8}` all
 * come out readable.
 */
class UnicodeRichTextRenderer : RichTextRenderer {

    override fun render(source: String): String {
        if (source.isEmpty()) return source

        var text = source.replace(MATH_DELIMITER, "")
        text = expandBraced(text, FRACTION_COMMANDS, 2) { args -> args[0] + "/" + args[1] }
        text = expandBraced(text, listOf("\\sqrt"), 1) { args -> "\u221A(" + args[0] + ")" }
        text = expandBraced(text, TEXT_COMMANDS, 1) { args -> args[0] }
        text = expandBraced(text, DECORATION_COMMANDS, 1) { args -> args[0] }
        text = SUPERSCRIPT.replace(text) { match -> superscript(match.groupValues[1]) }
        text = SUBSCRIPT.replace(text) { match -> subscript(match.groupValues[1]) }
        text = text.replace("\\left", "").replace("\\right", "")
        for ((command, symbol) in SYMBOLS) {
            if (text.contains(command)) text = text.replace(command, symbol)
        }
        text = UNKNOWN_COMMAND.replace(text) { match -> match.groupValues[1] }
        text = text.replace("{", "").replace("}", "").replace("\\", "")
        return collapse(text)
    }

    override fun hasMaths(source: String): Boolean =
        source.contains(MATH_DELIMITER) || source.contains('\\') || source.any { it.code > 0x0370 }

    private fun superscript(body: String): String {
        val trimmed = body.trim()
        val rendered = StringBuilder()
        var negative = false
        for ((index, character) in trimmed.withIndex()) {
            when {
                index == 0 && character == '-' -> negative = true
                SUPERSCRIPT_DIGITS.containsKey(character) -> rendered.append(SUPERSCRIPT_DIGITS[character])
                else -> return if (trimmed.length <= 1) "^$trimmed" else "^($trimmed)"
            }
        }
        if (rendered.isEmpty()) return if (trimmed.length <= 1) "^$trimmed" else "^($trimmed)"
        return (if (negative) "\u207B" else "") + rendered.toString()
    }

    private fun subscript(body: String): String {
        val trimmed = body.trim()
        val rendered = StringBuilder()
        for (character in trimmed) {
            val mapped = SUBSCRIPT_DIGITS[character] ?: return if (trimmed.length <= 1) "_$trimmed" else "_($trimmed)"
            rendered.append(mapped)
        }
        return if (rendered.isEmpty()) "_$trimmed" else rendered.toString()
    }

    private fun expandBraced(
        input: String,
        commands: List<String>,
        arity: Int,
        rewrite: (List<String>) -> String,
    ): String {
        var current = input
        var iterations = 0
        while (iterations++ < MAX_EXPANSIONS) {
            val before = current
            current = expandOnce(current, commands, arity, rewrite)
            if (current == before) return current
        }
        return current
    }

    private fun expandOnce(
        input: String,
        commands: List<String>,
        arity: Int,
        rewrite: (List<String>) -> String,
    ): String {
        for (command in commands) {
            val index = input.indexOf(command)
            if (index < 0) continue

            var cursor = index + command.length
            val arguments = ArrayList<String>(arity)
            var complete = true
            while (arguments.size < arity) {
                while (cursor < input.length && input[cursor].isWhitespace()) cursor++
                if (cursor >= input.length || input[cursor] != '{') {
                    complete = false
                    break
                }
                val braced = readBraced(input, cursor)
                if (braced == null) {
                    complete = false
                    break
                }
                arguments.add(braced.first)
                cursor = braced.second
            }
            if (!complete) continue
            return input.substring(0, index) + rewrite(arguments) + input.substring(cursor)
        }
        return input
    }

    private fun readBraced(input: String, openIndex: Int): Pair<String, Int>? {
        var depth = 0
        var index = openIndex
        while (index < input.length) {
            when (input[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return input.substring(openIndex + 1, index) to (index + 1)
                }

                else -> Unit
            }
            index++
        }
        return null
    }

    private fun collapse(text: String): String = WHITESPACE.replace(text, " ").trim()

    private companion object {
        const val MATH_DELIMITER = "\u0024"
        const val MAX_EXPANSIONS = 64

        val WHITESPACE = Regex("""\s+""")
        val SUPERSCRIPT = Regex("""\^\{([^{}]*)\}""")
        val SUBSCRIPT = Regex("""_\{([^{}]*)\}""")
        val UNKNOWN_COMMAND = Regex("""\\([a-zA-Z]+)""")

        val FRACTION_COMMANDS = listOf("\\dfrac", "\\tfrac", "\\frac")
        val TEXT_COMMANDS = listOf("\\text", "\\mathrm", "\\mathbf", "\\mathit", "\\mbox", "\\operatorname")
        val DECORATION_COMMANDS = listOf("\\vec", "\\hat", "\\bar", "\\dot", "\\ddot", "\\overline", "\\underline")

        val SUPERSCRIPT_DIGITS = mapOf(
            '0' to "\u2070", '1' to "\u00B9", '2' to "\u00B2", '3' to "\u00B3", '4' to "\u2074",
            '5' to "\u2075", '6' to "\u2076", '7' to "\u2077", '8' to "\u2078", '9' to "\u2079",
        )

        val SUBSCRIPT_DIGITS = mapOf(
            '0' to "\u2080", '1' to "\u2081", '2' to "\u2082", '3' to "\u2083", '4' to "\u2084",
            '5' to "\u2085", '6' to "\u2086", '7' to "\u2087", '8' to "\u2088", '9' to "\u2089",
        )

        val SYMBOLS: List<Pair<String, String>> = mapOf(
            "\\longrightarrow" to "\u2192",
            "\\rightarrow" to "\u2192",
            "\\leftarrow" to "\u2190",
            "\\leftrightarrow" to "\u2194",
            "\\infty" to "\u221E",
            "\\propto" to "\u221D",
            "\\subseteq" to "\u2286",
            "\\emptyset" to "\u2205",
            "\\therefore" to "\u2234",
            "\\because" to "\u2235",
            "\\epsilon" to "\u03B5",
            "\\varepsilon" to "\u03B5",
            "\\upsilon" to "\u03C5",
            "\\lambda" to "\u03BB",
            "\\vartheta" to "\u03D1",
            "\\varphi" to "\u03D5",
            "\\sigma" to "\u03C3",
            "\\theta" to "\u03B8",
            "\\alpha" to "\u03B1",
            "\\omega" to "\u03C9",
            "\\gamma" to "\u03B3",
            "\\delta" to "\u03B4",
            "\\kappa" to "\u03BA",
            "\\beta" to "\u03B2",
            "\\zeta" to "\u03B6",
            "\\iota" to "\u03B9",
            "\\times" to "\u00D7",
            "\\approx" to "\u2248",
            "\\equiv" to "\u2261",
            "\\Delta" to "\u0394",
            "\\Gamma" to "\u0393",
            "\\Lambda" to "\u039B",
            "\\Sigma" to "\u03A3",
            "\\Theta" to "\u0398",
            "\\Omega" to "\u03A9",
            "\\nabla" to "\u2207",
            "\\partial" to "\u2202",
            "\\qquad" to "  ",
            "\\degree" to "\u00B0",
            "\\subset" to "\u2282",
            "\\cdot" to "\u00B7",
            "\\cosec" to "cosec",
            "\\quad" to " ",
            "\\neq" to "\u2260",
            "\\leq" to "\u2264",
            "\\geq" to "\u2265",
            "\\int" to "\u222B",
            "\\sum" to "\u2211",
            "\\prod" to "\u220F",
            "\\rho" to "\u03C1",
            "\\tau" to "\u03C4",
            "\\eta" to "\u03B7",
            "\\phi" to "\u03C6",
            "\\chi" to "\u03C7",
            "\\psi" to "\u03C8",
            "\\Phi" to "\u03A6",
            "\\Psi" to "\u03A8",
            "\\Pi" to "\u03A0",
            "\\cup" to "\u222A",
            "\\cap" to "\u2229",
            "\\cos" to "cos",
            "\\sin" to "sin",
            "\\tan" to "tan",
            "\\sec" to "sec",
            "\\cot" to "cot",
            "\\log" to "log",
            "\\exp" to "exp",
            "\\circ" to "\u00B0",
            "\\div" to "\u00F7",
            "\\le" to "\u2264",
            "\\ge" to "\u2265",
            "\\ne" to "\u2260",
            "\\pm" to "\u00B1",
            "\\mp" to "\u2213",
            "\\pi" to "\u03C0",
            "\\mu" to "\u03BC",
            "\\nu" to "\u03BD",
            "\\xi" to "\u03BE",
            "\\in" to "\u2208",
            "\\ln" to "ln",
            "\\," to " ",
            "\\;" to " ",
            "\\:" to " ",
            "\\!" to "",
            "\\%" to "%",
        ).entries.sortedByDescending { it.key.length }.map { it.key to it.value }
    }
}
