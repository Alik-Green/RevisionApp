package com.revisionapp.math

/**
 * How a script or a root should be written when the output is a plain `String`.
 *
 * [UNICODE] is what the user sees: real superscripts, subscripts and a radical
 * sign. [ASCII] is what the answer checker compares: caret and underscore
 * notation, so that `x^2` from LaTeX, `x²` typed directly and `x**2` typed as a
 * shortcut all canonicalise to the same `x^2`.
 */
enum class ScriptStyle {
    UNICODE,
    ASCII,
}

/**
 * The fallback renderer: maths as a plain `String`.
 *
 * Used for search snippets, for the answer checker's canonical form, for any
 * context that cannot lay text out, and as the safety net when the Compose
 * renderer throws. Scripts become real Unicode superscripts and subscripts when
 * every character in them has an equivalent (`x^2` to `x²`, `log_3` to `log₃`,
 * `x_{n+1}` to `xₙ₊₁`) and caret notation when they do not, because one real
 * superscript next to a literal `+1` reads worse than either.
 *
 * Fractions cannot be stacked in a plain string, so they become `(a)/(b)`.
 */
object MathPlainText {

    fun render(nodes: List<MathNode>, style: ScriptStyle = ScriptStyle.UNICODE): String =
        buildString { appendAll(nodes, style) }

    fun render(source: String, style: ScriptStyle = ScriptStyle.UNICODE): String =
        render(MathParser.parse(source), style)

    private fun StringBuilder.appendAll(nodes: List<MathNode>, style: ScriptStyle) {
        for (node in nodes) appendOne(node, style)
    }

    private fun StringBuilder.appendOne(node: MathNode, style: ScriptStyle) {
        when (node) {
            is MathNode.Run -> append(node.text)

            // An unsupported command shows as its name. Never as a backslash.
            is MathNode.Unknown -> append(node.name)

            is MathNode.Group -> appendAll(node.children, style)

            is MathNode.Script -> {
                appendAll(node.base, style)
                val sup = node.sup
                if (sup != null) appendScript(render(sup, style), style, caret = '^', unicode = MathUnicode::superscript)
                val sub = node.sub
                if (sub != null) appendScript(render(sub, style), style, caret = '_', unicode = MathUnicode::subscript)
            }

            is MathNode.Fraction -> {
                append("(")
                appendAll(node.numerator, style)
                append(")/(")
                appendAll(node.denominator, style)
                append(")")
            }

            is MathNode.Radical -> appendRadical(node, style)
        }
    }

    private fun StringBuilder.appendScript(
        body: String,
        style: ScriptStyle,
        caret: Char,
        unicode: (String) -> String?,
    ) {
        if (style == ScriptStyle.UNICODE) {
            val mapped = unicode(body)
            if (mapped != null) {
                append(mapped)
                return
            }
        }
        append(caret)
        append(if (needsBrackets(body)) "($body)" else body)
    }

    private fun StringBuilder.appendRadical(node: MathNode.Radical, style: ScriptStyle) {
        val index = node.index?.let { render(it, style) }
        val body = render(node.body, style)
        if (style == ScriptStyle.ASCII) {
            if (index == null || index == "2") {
                append("sqrt(").append(body).append(")")
            } else {
                append("root(").append(index).append(")(").append(body).append(")")
            }
            return
        }
        append(
            when (index) {
                null, "2" -> "\u221A"
                "3" -> "\u221B"
                "4" -> "\u221C"
                else -> (MathUnicode.superscript(index) ?: "^$index") + "\u221A"
            },
        )
        if (body.length > 1) append("(").append(body).append(")") else append(body)
    }

    /**
     * Whether a script body needs brackets in caret notation.
     *
     * `x^-1` reads fine without them, `x^(n+1)` does not: a leading sign is not an
     * operator that needs grouping. Public because the answer checker folds typed
     * Unicode scripts (`xₙ₊₁`) back to caret notation and the two must agree, or
     * `x_{n+1}` and `xₙ₊₁` would canonicalise differently and fail to match.
     */
    fun needsBrackets(body: String): Boolean {
        if (body.length <= 1) return false
        val afterLeadingSign = if (body[0] == '+' || body[0] == '-') body.substring(1) else body
        return afterLeadingSign.any { it == '+' || it == '-' || it == '*' || it == '/' }
    }
}
