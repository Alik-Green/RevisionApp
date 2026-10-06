package com.revisionapp.domain.check

/**
 * Structural comparison for [com.revisionapp.domain.model.AnswerType.EXPRESSION]
 * cards.
 *
 * Expressions are canonicalised — LaTeX flattened, whitespace and thousands
 * separators removed, multiplication made explicit, redundant brackets dropped,
 * negative powers moved into the denominator, and commutative operators sorted —
 * so all of these agree:
 *
 * - `2*x*y`, `y*2*x`, `x*2*y`
 * - `a-b` and `-b+a`
 * - `\frac{a}{b}` and `a/b`
 * - `m*s^-1` and `m/s`
 *
 * Two things are deliberately out of scope: implicit multiplication (`2xy` is not
 * read as `2*x*y`) and algebraic expansion (`(x+1)(x-1)` versus `x^2-1`). For
 * those [equivalent] returns false and the caller falls back to self-grading
 * rather than guessing.
 */
object ExpressionComparing {

    fun canonicalise(raw: String): String {
        val flat = TextNormaliser.toPlainText(raw)
            .lowercase()
            .replace(",", "")
            .replace(WHITESPACE, "")
            .replace("**", "^")
        if (flat.isEmpty()) return flat

        val terms = splitTopLevel(flat, PLUS, MINUS)
        val canonicalTerms = terms.map { (sign, body) ->
            val numerator = ArrayList<String>()
            val denominator = ArrayList<String>()
            for ((operator, factor) in splitTopLevel(body, TIMES, DIVIDE)) {
                val (text, invert) = placeFactor(factor)
                // A factor goes below the line if it was divided, or if it carries
                // a negative power — unless both apply, which cancels out.
                if ((operator == DIVIDE) != invert) denominator.add(text) else numerator.add(text)
            }
            numerator.sort()
            denominator.sort()
            // "x^-1" on its own is the same as "1/x", so give an empty numerator
            // an explicit 1 rather than emitting "/x".
            val head = if (numerator.isEmpty() && denominator.isNotEmpty()) "1" else numerator.joinToString(TIMES.toString())
            val canonicalBody = head + denominator.joinToString("") { "/$it" }
            if (sign == MINUS) "-$canonicalBody" else canonicalBody
        }
        return canonicalTerms.sorted().joinToString(PLUS.toString())
    }

    fun equivalent(left: String, right: String): Boolean {
        val canonicalLeft = canonicalise(left)
        val canonicalRight = canonicalise(right)
        return canonicalLeft.isNotEmpty() && canonicalLeft == canonicalRight
    }

    private val WHITESPACE = Regex("""\s+""")
    private val NEGATIVE_POWER = Regex("""(.+)\^-(\d+)""")

    private const val PLUS = '+'
    private const val MINUS = '-'
    private const val TIMES = '*'
    private const val DIVIDE = '/'

    /**
     * Strips redundant brackets from one multiplicative factor and moves a
     * negative power into the denominator, so `s^-1` and `1/s` agree.
     *
     * @return the canonical text, and whether it belongs on the other side of the
     *   division from the one its operator implies.
     */
    private fun placeFactor(factor: String): Pair<String, Boolean> {
        val stripped = stripRedundantBrackets(factor)
        val negativePower = NEGATIVE_POWER.matchEntire(stripped)
            ?: return (stripped.removeSuffix("^1") to false)
        val base = negativePower.groupValues[1]
        val exponent = negativePower.groupValues[2]
        val text = if (exponent == "1") base else "$base^$exponent"
        return text to true
    }

    /**
     * Splits on [operators] that appear at bracket depth zero. Each segment keeps
     * the operator that preceded it; the first segment defaults to `+`.
     *
     * A sign immediately after `^` is part of the exponent, not an operator, so
     * `m*s^-1` stays in one piece.
     */
    private fun splitTopLevel(text: String, vararg operators: Char): List<Pair<Char, String>> {
        val segments = ArrayList<Pair<Char, String>>()
        var depth = 0
        var sign = PLUS
        val current = StringBuilder()

        for (character in text) {
            when {
                character == '(' || character == '[' -> {
                    depth++
                    current.append(character)
                }

                character == ')' || character == ']' -> {
                    if (depth > 0) depth--
                    current.append(character)
                }

                depth == 0 && operators.contains(character) && current.lastOrNull() != '^' -> {
                    if (current.isNotEmpty()) segments.add(sign to current.toString())
                    sign = character
                    current.setLength(0)
                }

                else -> current.append(character)
            }
        }
        if (current.isNotEmpty()) segments.add(sign to current.toString())
        return segments
    }

    private fun stripRedundantBrackets(body: String): String {
        var current = body
        while (current.length >= 2 && current.first() == '(' && current.last() == ')' && wrapsWhole(current)) {
            val inner = current.substring(1, current.length - 1)
            if (splitTopLevel(inner, PLUS, MINUS, TIMES, DIVIDE).size > 1) break
            current = inner
        }
        return current
    }

    /** True when the leading `(` of [text] is the one closed by the trailing `)`. */
    private fun wrapsWhole(text: String): Boolean {
        var depth = 0
        for (index in text.indices) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0 && index != text.lastIndex) return false
                }

                else -> Unit
            }
        }
        return depth == 0
    }
}
