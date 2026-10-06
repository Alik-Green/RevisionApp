package com.revisionapp.domain.check

/**
 * Turns authored content and typed answers into a comparable form.
 *
 * Three layers, in order:
 *
 * 1. [toPlainText] — strips the `$...$` LaTeX delimiters and rewrites maths into
 *    readable text (`\frac{a}{b}` -> `(a)/(b)`, `\theta` -> `theta`, `^{2}` ->
 *    `^2`). This is also what the Unicode [com.revisionapp.ui.render.RichTextRenderer]
 *    uses for display, so what the user sees is what gets compared.
 * 2. [normalise] — lowercases, maps unicode maths symbols, turns punctuation into
 *    spaces and collapses the runs, while keeping what changes an answer's
 *    meaning: the operators `+ * / ^ = < > % _`, a decimal point, a minus sign
 *    (as opposed to a word hyphen) and a prime (as opposed to the apostrophe in a
 *    contraction).
 * 3. [matchTokens] — splits, maps number words to digits and applies [Stemmer].
 */
object TextNormaliser {

    /** Characters that stay inside a token because they change its meaning. */
    private const val OPERATOR_CHARACTERS = "+*/^=<>%_"

    private val LATEX_COMMANDS: List<Pair<String, String>> = mapOf(
        "\\longrightarrow" to "->",
        "\\longleftarrow" to "<-",
        "\\leftrightarrow" to "<->",
        "\\rightarrow" to "->",
        "\\leftarrow" to "<-",
        "\\Rightarrow" to "=>",
        "\\Leftarrow" to "<=",
        "\\subseteq" to "subset of",
        "\\emptyset" to "empty set",
        "\\therefore" to "therefore",
        "\\because" to "because",
        "\\operatorname" to "",
        "\\infty" to "infinity",
        "\\propto" to "proportional to",
        "\\notin" to "not in",
        "\\qquad" to " ",
        "\\degree" to "deg",
        "\\partial" to "d",
        "\\epsilon" to "epsilon",
        "\\upsilon" to "upsilon",
        "\\lambda" to "lambda",
        "\\varphi" to "phi",
        "\\vartheta" to "theta",
        "\\sigma" to "sigma",
        "\\subset" to "subset",
        "\\nabla" to "grad",
        "\\kappa" to "kappa",
        "\\gamma" to "gamma",
        "\\delta" to "delta",
        "\\theta" to "theta",
        "\\alpha" to "alpha",
        "\\omega" to "omega",
        "\\times" to "*",
        "\\approx" to "~",
        "\\equiv" to "=",
        "\\Delta" to "delta",
        "\\Gamma" to "gamma",
        "\\Lambda" to "lambda",
        "\\Sigma" to "sum",
        "\\Theta" to "theta",
        "\\right" to "",
        "\\union" to "union",
        "\\cdot" to "*",
        "\\sinh" to "sinh",
        "\\cosh" to "cosh",
        "\\tanh" to "tanh",
        "\\cosec" to "cosec",
        "\\Omega" to "ohm",
        "\\quad" to " ",
        "\\neq" to "!=",
        "\\leq" to "<=",
        "\\geq" to ">=",
        "\\int" to "integral",
        "\\phi" to "phi",
        "\\chi" to "chi",
        "\\psi" to "psi",
        "\\rho" to "rho",
        "\\tau" to "tau",
        "\\sum" to "sum",
        "\\eta" to "eta",
        "\\zeta" to "zeta",
        "\\iota" to "iota",
        "\\beta" to "beta",
        "\\left" to "",
        "\\prod" to "product",
        "\\circ" to "deg",
        "\\cos" to "cos",
        "\\sin" to "sin",
        "\\tan" to "tan",
        "\\sec" to "sec",
        "\\cot" to "cot",
        "\\log" to "log",
        "\\exp" to "exp",
        "\\cap" to "intersection",
        "\\cup" to "union",
        "\\div" to "/",
        "\\pm" to "+/-",
        "\\mp" to "-/+",
        "\\ne" to "!=",
        "\\le" to "<=",
        "\\ge" to ">=",
        "\\pi" to "pi",
        "\\Pi" to "pi",
        "\\Phi" to "phi",
        "\\Psi" to "psi",
        "\\mu" to "mu",
        "\\nu" to "nu",
        "\\xi" to "xi",
        "\\in" to "in",
        "\\ln" to "ln",
        "\\lg" to "lg",
        "\\%" to "%",
        "\\$" to "$",
        "\\&" to "&",
        "\\#" to "#",
        "\\," to " ",
        "\\;" to " ",
        "\\:" to " ",
        "\\!" to "",
        "\\ " to " ",
    ).entries.sortedByDescending { it.key.length }.map { it.key to it.value }

    /** Commands that take one or two `{...}` arguments, and how to rewrite them. */
    private val BRACED_COMMANDS: Map<String, Int> = linkedMapOf(
        "\\dfrac" to 2,
        "\\tfrac" to 2,
        "\\frac" to 2,
        "\\sqrt" to 1,
        "\\text" to 1,
        "\\mathrm" to 1,
        "\\mathbf" to 1,
        "\\mathit" to 1,
        "\\mathsf" to 1,
        "\\mbox" to 1,
        "\\vec" to 1,
        "\\hat" to 1,
        "\\bar" to 1,
        "\\dot" to 1,
        "\\ddot" to 1,
        "\\overline" to 1,
        "\\underline" to 1,
        "\\unit" to 1,
    )

    private val ENVIRONMENTS = Regex("""\\(begin|end)\{[a-zA-Z*]+\}""")
    private val SUPERSCRIPT_GROUP = Regex("""\^\{([^{}]*)\}""")
    private val SUBSCRIPT_GROUP = Regex("""_\{([^{}]*)\}""")
    private val UNKNOWN_COMMAND = Regex("""\\([a-zA-Z]+)""")
    private val WHITESPACE_RUN = Regex("""\s+""")

    private val UNICODE_MATHS: Map<Char, String> = mapOf(
        '\u00D7' to "*", // ×
        '\u00B7' to "*", // ·
        '\u22C5' to "*", // ⋅
        '\u00F7' to "/", // ÷
        '\u2212' to "-", // −
        '\u2013' to "-", // –
        '\u2014' to " ", // —
        '\u2264' to "<=", // ≤
        '\u2265' to ">=", // ≥
        '\u2260' to "!=", // ≠
        '\u2248' to "~", // ≈
        '\u2261' to "=", // ≡
        '\u00B1' to "+/-", // ±
        '\u2213' to "-/+", // ∓
        '\u221A' to "sqrt", // √
        '\u221B' to "cbrt", // ∛
        '\u221D' to "proportional to", // ∝
        '\u221E' to "infinity", // ∞
        '\u2208' to "in", // ∈
        '\u2286' to "subset of", // ⊆
        '\u222A' to "union", // ∪
        '\u2229' to "intersection", // ∩
        '\u2234' to "therefore", // ∴
        '\u2235' to "because", // ∵
        '\u222B' to "integral", // ∫
        '\u2211' to "sum", // ∑
        '\u220F' to "product", // ∏
        '\u2202' to "d", // ∂
        '\u2207' to "grad", // ∇
        '\u00B0' to "deg", // °
        '\u2192' to "->", // →
        '\u2190' to "<-", // ←
        '\u21D2' to "=>", // ⇒
        '\u03C0' to "pi", // π
        '\u03B8' to "theta", // θ
        '\u03B1' to "alpha",
        '\u03B2' to "beta",
        '\u03B3' to "gamma",
        '\u0393' to "gamma",
        '\u03B4' to "delta",
        '\u0394' to "delta",
        '\u03B5' to "epsilon",
        '\u03B6' to "zeta",
        '\u03B7' to "eta",
        '\u03BB' to "lambda",
        '\u039B' to "lambda",
        '\u03BC' to "mu",
        '\u03BD' to "nu",
        '\u03BE' to "xi",
        '\u03C1' to "rho",
        '\u03C3' to "sigma",
        '\u03A3' to "sum",
        '\u03C4' to "tau",
        '\u03C6' to "phi",
        '\u03A6' to "phi",
        '\u03C7' to "chi",
        '\u03C8' to "psi",
        '\u03A8' to "psi",
        '\u03C9' to "omega",
        '\u03A9' to "ohm",
        '\u00B2' to "^2",
        '\u00B3' to "^3",
        '\u00B9' to "^1",
        '\u2070' to "^0",
        '\u2074' to "^4",
        '\u2075' to "^5",
        '\u2076' to "^6",
        '\u2077' to "^7",
        '\u2078' to "^8",
        '\u2079' to "^9",
        '\u2080' to "_0",
        '\u2081' to "_1",
        '\u2082' to "_2",
        '\u2083' to "_3",
        '\u2084' to "_4",
        '\u2085' to "_5",
        '\u2086' to "_6",
        '\u2087' to "_7",
        '\u2088' to "_8",
        '\u2089' to "_9",
        '\u2032' to "'",
        '\u00A0' to " ",
    )

    private val NUMBER_WORDS: Map<String, String> = mapOf(
        "zero" to "0", "nought" to "0", "nil" to "0",
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
        "eleven" to "11", "twelve" to "12", "thirteen" to "13", "fourteen" to "14",
        "fifteen" to "15", "sixteen" to "16", "seventeen" to "17", "eighteen" to "18",
        "nineteen" to "19", "twenty" to "20", "thirty" to "30", "forty" to "40",
        "fifty" to "50", "sixty" to "60", "seventy" to "70", "eighty" to "80",
        "ninety" to "90", "hundred" to "100", "thousand" to "1000", "million" to "1000000",
    )

    /**
     * Rewrites LaTeX and unicode maths into readable plain text. Operators and
     * structure are preserved so that [ExpressionComparing] can still work on the
     * result.
     */
    fun toPlainText(raw: String): String {
        if (raw.isEmpty()) return raw

        var text = raw.replace("$", "")
        text = ENVIRONMENTS.replace(text, " ")
        text = expandBracedCommands(text)
        for ((command, replacement) in LATEX_COMMANDS) {
            // Padded with spaces so that "2\pi r" becomes "2 pi r" rather than
            // "2pi r"; runs of whitespace are collapsed at the end.
            if (text.contains(command)) text = text.replace(command, " $replacement ")
        }
        text = text.replace("\\\\", " ")
        text = SUPERSCRIPT_GROUP.replace(text) { "^" + group(it.groupValues[1]) }
        text = SUBSCRIPT_GROUP.replace(text) { "_" + group(it.groupValues[1]) }
        text = UNKNOWN_COMMAND.replace(text) { it.groupValues[1] }
        text = text.replace("{", " ").replace("}", " ").replace("\\", " ")

        val builder = StringBuilder(text.length)
        for (character in text) {
            builder.append(UNICODE_MATHS[character] ?: character.toString())
        }
        return WHITESPACE_RUN.replace(builder.toString(), " ").trim()
    }

    /**
     * Keeps `^{2}` and `^{-1}` unparenthesised (so `s^{-1}` and `s^-1` agree) but
     * parenthesises anything that would change meaning, e.g. `x^{n+1}` ->
     * `x^(n+1)` rather than `x^n+1`.
     */
    private fun group(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "()"
        val afterLeadingSign = if (trimmed[0] == '+' || trimmed[0] == '-') trimmed.substring(1) else trimmed
        val hasOperator = afterLeadingSign.any { it == '+' || it == '-' || it == '*' || it == '/' }
        return if (hasOperator) "($trimmed)" else trimmed
    }

    /** Expands `\frac{a}{b}` and friends, innermost-out, with a hard iteration cap. */
    private fun expandBracedCommands(input: String): String {
        var current = input
        var iterations = 0
        while (iterations++ < MAX_EXPANSIONS) {
            val expanded = expandOnce(current)
            if (expanded == current) return current
            current = expanded
        }
        return current
    }

    private fun expandOnce(input: String): String {
        for ((command, arity) in BRACED_COMMANDS) {
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

            val replacement = when (command) {
                "\\frac", "\\dfrac", "\\tfrac" -> "(" + arguments[0] + ")/(" + arguments[1] + ")"
                "\\sqrt" -> "sqrt(" + arguments[0] + ")"
                else -> arguments[0]
            }
            return input.substring(0, index) + replacement + input.substring(cursor)
        }
        return input
    }

    /** Reads `{...}` starting at [openIndex], honouring nesting. */
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

    /**
     * Canonical form used for exact and alias comparison: lowercased, punctuation
     * turned into spaces (meaningful operators excepted), whitespace collapsed.
     */
    fun normalise(raw: String): String {
        val plain = toPlainText(raw).lowercase()
        val builder = StringBuilder(plain.length)
        for (index in plain.indices) {
            val character = plain[index]
            when {
                character.isLetterOrDigit() -> builder.append(character)
                // doesn't -> doesnt, so that contractions keep their negation, but
                // f'(x) keeps its prime: in maths it changes the meaning, and losing
                // it would make "f(a)" and "f'(a)" the same answer.
                character == '\'' -> if (isPrime(plain, index)) builder.append('\'')
                // A hyphen survives when it is a sign rather than a word joiner:
                // "-4.5", "5-3" and "= -a" stay intact, "well-known" splits.
                character == '-' -> if (isSign(plain, index, builder)) builder.append('-') else builder.append(' ')
                // Likewise a full stop only survives as a decimal point, so that a
                // model answer ending in "." still matches one that does not.
                character == '.' -> if (isDecimalPoint(plain, index, builder)) builder.append('.') else builder.append(' ')
                OPERATOR_CHARACTERS.indexOf(character) >= 0 -> builder.append(character)
                else -> builder.append(' ')
            }
        }
        return WHITESPACE_RUN.replace(builder.toString(), " ").trim()
    }

    /** An apostrophe is a prime unless another letter follows it. */
    private fun isPrime(text: String, index: Int): Boolean {
        val next = text.getOrNull(index + 1) ?: return true
        return !next.isLetter()
    }

    private fun isDecimalPoint(text: String, index: Int, builder: StringBuilder): Boolean {
        val next = text.getOrNull(index + 1) ?: return false
        val previous = builder.lastOrNull() ?: return false
        return next.isDigit() && previous.isDigit()
    }

    /**
     * True when the hyphen at [index] is a minus sign rather than a word joiner.
     *
     * Between two word characters it joins them, so "well-known" and
     * "centre-seeking" split into two tokens. Anywhere else in front of a term it
     * is a sign and carries meaning: "-4.5", "5-3", "s^-1" and, importantly for
     * maths content, "= -a", which must not collapse into "= a".
     */
    private fun isSign(text: String, index: Int, builder: StringBuilder): Boolean {
        val next = text.getOrNull(index + 1) ?: return false
        if (!next.isLetterOrDigit() && next != '(') return false
        val previous = builder.lastOrNull() ?: return true
        if (previous.isLetter()) return false
        return previous == ' ' || previous.isDigit() || previous in "=({[+*/^_"
    }

    /** Splits [raw] into tokens, trimming punctuation that only ends a sentence. */
    fun tokens(raw: String): List<String> =
        normalise(raw).split(' ').filter { it.isNotEmpty() }.map { trimEdges(it) }

    /**
     * The token form used for matching: number words become digits and every token
     * is reduced with [Stemmer].
     */
    fun matchTokens(raw: String): List<String> =
        tokens(raw).map { NUMBER_WORDS[it] ?: it }.map { Stemmer.stem(it) }

    fun matchTokenSet(raw: String): Set<String> = matchTokens(raw).toSet()

    private fun trimEdges(token: String): String {
        var start = 0
        var end = token.length
        while (end - start > 1 && token[end - 1].let { !it.isLetterOrDigit() && it != '^' && it != '_' }) end--
        while (end - start > 1 && token[start].let { !it.isLetterOrDigit() && it != '-' && it != '+' }) start++
        return token.substring(start, end)
    }

    private const val MAX_EXPANSIONS = 64
}
