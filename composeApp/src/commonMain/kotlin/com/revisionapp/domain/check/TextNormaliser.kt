package com.revisionapp.domain.check

import com.revisionapp.math.MathParser
import com.revisionapp.math.MathPlainText
import com.revisionapp.math.ScriptStyle

/**
 * Turns authored content and typed answers into a comparable form.
 *
 * Three layers, in order:
 *
 * 1. [toPlainText] — parses LaTeX with the same [MathParser] the display renderer
 *    uses and re-emits it in canonical ASCII (`\frac{a}{b}` -> `(a)/(b)`,
 *    `\theta` -> `theta`, `x^{2}` -> `x^2`), folding Unicode scripts back to caret
 *    notation and expanding the `**` and `+-` typing shortcuts. Sharing the parser
 *    is what makes "what the user sees is what gets compared" true rather than a
 *    comment: the old hand-written replacement table drifted from the renderer,
 *    which is how `\Rightarrow` came to be displayed as the word "Rightarrow".
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

    private val ENVIRONMENTS = Regex("""\\(begin|end)\{[a-zA-Z*]+\}""")
    private val SUPERSCRIPT_RUN = Regex("[\u00B9\u00B2\u00B3\u2070-\u207F]+")
    private val SUBSCRIPT_RUN = Regex("[\u1D62-\u1D65\u2080-\u2089\u208A-\u208E\u2090-\u209C]+")

    private val SUPERSCRIPT_ASCII: Map<Char, Char> = mapOf(
        '\u2070' to '0', '\u00B9' to '1', '\u00B2' to '2', '\u00B3' to '3', '\u2074' to '4',
        '\u2075' to '5', '\u2076' to '6', '\u2077' to '7', '\u2078' to '8', '\u2079' to '9',
        '\u207A' to '+', '\u207B' to '-', '\u207C' to '=', '\u207D' to '(', '\u207E' to ')',
        '\u2071' to 'i', '\u207F' to 'n',
    )

    private val SUBSCRIPT_ASCII: Map<Char, Char> = mapOf(
        '\u2080' to '0', '\u2081' to '1', '\u2082' to '2', '\u2083' to '3', '\u2084' to '4',
        '\u2085' to '5', '\u2086' to '6', '\u2087' to '7', '\u2088' to '8', '\u2089' to '9',
        '\u208A' to '+', '\u208B' to '-', '\u208C' to '=', '\u208D' to '(', '\u208E' to ')',
        '\u2090' to 'a', '\u2091' to 'e', '\u2092' to 'o', '\u2093' to 'x', '\u2095' to 'h',
        '\u2096' to 'k', '\u2097' to 'l', '\u2098' to 'm', '\u2099' to 'n', '\u209A' to 'p',
        '\u209B' to 's', '\u209C' to 't', '\u1D62' to 'i', '\u1D63' to 'r', '\u1D64' to 'u',
        '\u1D65' to 'v',
    )

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
     * Rewrites LaTeX and unicode maths into readable plain text, in the canonical
     * ASCII form the checker compares. Operators and structure survive so that
     * [ExpressionComparing] can still work on the result.
     */
    fun toPlainText(raw: String): String {
        if (raw.isEmpty()) return raw

        val withoutEnvironments = ENVIRONMENTS.replace(raw, " ")
        val ascii = MathPlainText.render(MathParser.parse(applyShortcuts(withoutEnvironments)), ScriptStyle.ASCII)
        val folded = foldScripts(ascii)

        val builder = StringBuilder(folded.length)
        for (character in folded) {
            val replacement = UNICODE_MATHS[character]
            when {
                replacement == null -> builder.append(character)
                // Multi-character replacements are padded so that "2\pi r" collapses
                // to "2 pi r" rather than "2pi r".
                replacement.length > 1 -> builder.append(' ').append(replacement).append(' ')
                else -> builder.append(replacement)
            }
        }
        return WHITESPACE_RUN.replace(builder.toString(), " ").trim()
    }

    /**
     * Typed shortcuts for maths a keyboard makes awkward. Arrows and relations need
     * none: `->`, `=>`, `<=` and `>=` already canonicalise to exactly what
     * `\rightarrow`, `\Rightarrow`, `\leq` and `\geq` produce.
     */
    private fun applyShortcuts(raw: String): String = raw
        .replace("**", "^")
        .replace("+-", "+/-")

    /**
     * Folds runs of Unicode script characters back to caret and underscore
     * notation, so a pasted `x²` compares equal to an authored `x^2`. Runs are
     * folded whole because `10⁻¹⁹` has to become `10^-19`, not `10^-^1^9`.
     */
    private fun foldScripts(text: String): String {
        val superscripts = SUPERSCRIPT_RUN.replace(text) { match -> script("^", match.value, SUPERSCRIPT_ASCII) }
        return SUBSCRIPT_RUN.replace(superscripts) { match -> script("_", match.value, SUBSCRIPT_ASCII) }
    }

    /** Brackets a folded run exactly as [MathPlainText] brackets a parsed one. */
    private fun script(marker: String, run: String, table: Map<Char, Char>): String {
        val builder = StringBuilder(run.length)
        for (character in run) builder.append(table[character] ?: character)
        val body = builder.toString()
        return marker + if (MathPlainText.needsBrackets(body)) "($body)" else body
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
}
