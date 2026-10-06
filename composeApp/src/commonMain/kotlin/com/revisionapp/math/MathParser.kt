package com.revisionapp.math

/**
 * Recursive-descent parser for the LaTeX subset the content packs use.
 *
 * Deliberately forgiving: an unbalanced brace ends the group instead of throwing,
 * a stray `}` is dropped, an unknown command becomes [MathNode.Unknown] and is
 * rendered as its name, and every code path terminates. Maths in a revision card
 * must never be able to crash the app or show a raw backslash.
 *
 * `$`, `\( \)` and `\[ \]` are treated as delimiters and removed, which means
 * bare `x^2` written without delimiters is handled too - and so a currency sign
 * would be eaten. That trade is recorded in docs/DECISIONS.md D32.
 */
object MathParser {

    fun parse(source: String): List<MathNode> {
        if (source.isEmpty()) return MathNode.Empty
        val cursor = Cursor(stripDelimiters(source))
        return tidy(parseNodes(cursor, terminator = null))
    }

    private fun parseNodes(cursor: Cursor, terminator: Char?): MutableList<MathNode> {
        val nodes = ArrayList<MathNode>()
        while (!cursor.done) {
            val character = cursor.peek()
            if (terminator != null && character == terminator) return nodes
            when (character) {
                '{' -> {
                    cursor.take()
                    nodes += MathNode.Group(parseNodes(cursor, terminator = '}'))
                    cursor.expect('}')
                }

                '}' -> cursor.take() // A stray close brace: drop it rather than fail.

                '^', '_' -> {
                    cursor.take()
                    val argument = parseScriptArgument(cursor)
                    val previous: MathNode? = if (nodes.isEmpty()) null else nodes.removeAt(nodes.lastIndex)
                    val existing = previous as? MathNode.Script
                    nodes += MathNode.Script(
                        base = existing?.base ?: listOfNotNull(previous),
                        sup = if (character == '^') (existing?.sup ?: emptyList()) + argument else existing?.sup,
                        sub = if (character == '_') (existing?.sub ?: emptyList()) + argument else existing?.sub,
                    )
                }

                '\\' -> parseCommand(cursor, nodes)

                else -> {
                    nodes += MathNode.Run(character.toString())
                    cursor.take()
                }
            }
        }
        return nodes
    }

    /** `^2`, `^{n+1}` and `^\alpha` are all legal; take exactly one token. */
    private fun parseScriptArgument(cursor: Cursor): List<MathNode> {
        if (cursor.done) return emptyList()
        return when (cursor.peek()) {
            '{' -> {
                cursor.take()
                val inner = parseNodes(cursor, terminator = '}')
                cursor.expect('}')
                inner
            }

            '\\' -> {
                val nodes = ArrayList<MathNode>(1)
                parseCommand(cursor, nodes)
                nodes
            }

            else -> listOf(MathNode.Run(cursor.take().toString()))
        }
    }

    private fun parseCommand(cursor: Cursor, nodes: MutableList<MathNode>) {
        cursor.take() // the backslash
        if (cursor.done) {
            // A trailing backslash with nothing after it: drop it, because the
            // rule for this renderer is that a backslash is never displayed.
            return
        }
        val first = cursor.peek()
        if (!first.isLetter()) {
            // \, \; \! \% \\ and friends: one non-letter character.
            cursor.take()
            nodes += MathNode.Run(MathUnicode.SPACING[first] ?: first.toString())
            return
        }

        val name = readName(cursor)
        when {
            name == "frac" || name == "dfrac" || name == "tfrac" -> nodes += MathNode.Fraction(
                numerator = parseBracedArgument(cursor),
                denominator = parseBracedArgument(cursor),
            )

            name == "sqrt" -> nodes += MathNode.Radical(
                index = parseBracketArgument(cursor),
                body = parseBracedArgument(cursor),
            )

            name in MathUnicode.TEXT_COMMANDS -> nodes += MathNode.Group(parseBracedArgument(cursor))
            name in MathUnicode.DECORATION_COMMANDS -> nodes += MathNode.Group(parseBracedArgument(cursor))
            name in MathUnicode.IGNORED_COMMANDS -> Unit

            else -> {
                val symbol = MathUnicode.COMMANDS[name]
                nodes += if (symbol != null) MathNode.Run(symbol) else MathNode.Unknown(name)
            }
        }
    }

    /** `\frac{a}{b}` normally, but `\frac12` is legal LaTeX too. */
    private fun parseBracedArgument(cursor: Cursor): List<MathNode> {
        cursor.skipWhitespace()
        if (cursor.done) return emptyList()
        if (cursor.peek() != '{') return parseScriptArgument(cursor)
        cursor.take()
        val inner = parseNodes(cursor, terminator = '}')
        cursor.expect('}')
        return inner
    }

    /** The optional `[n]` of `\sqrt[n]{x}`. */
    private fun parseBracketArgument(cursor: Cursor): List<MathNode>? {
        cursor.skipWhitespace()
        if (cursor.done || cursor.peek() != '[') return null
        cursor.take()
        val inner = parseNodes(cursor, terminator = ']')
        cursor.expect(']')
        return inner
    }

    private fun readName(cursor: Cursor): String {
        val builder = StringBuilder()
        while (!cursor.done && cursor.peek().isLetter()) builder.append(cursor.take())
        return builder.toString()
    }

    /** Merges neighbouring runs so `a`, `+`, `b` become one "a+b" node. */
    private fun tidy(nodes: List<MathNode>): List<MathNode> {
        val out = ArrayList<MathNode>(nodes.size)
        for (node in nodes) {
            val last = out.lastOrNull()
            if (node is MathNode.Run && last is MathNode.Run) {
                out[out.lastIndex] = MathNode.Run(last.text + node.text)
                continue
            }
            out += when (node) {
                is MathNode.Run -> node
                is MathNode.Unknown -> node
                is MathNode.Group -> MathNode.Group(tidy(node.children))
                is MathNode.Script -> MathNode.Script(
                    base = tidy(node.base),
                    sup = node.sup?.let { tidy(it) },
                    sub = node.sub?.let { tidy(it) },
                )

                is MathNode.Fraction -> MathNode.Fraction(tidy(node.numerator), tidy(node.denominator))
                is MathNode.Radical -> MathNode.Radical(node.index?.let { tidy(it) }, tidy(node.body))
            }
        }
        return out
    }

    private fun stripDelimiters(source: String): String = source
        .replace("$$", " ")
        .replace("$", "")
        .replace("\\(", " ")
        .replace("\\)", " ")
        .replace("\\[", " ")
        .replace("\\]", " ")

    private class Cursor(val text: String) {
        var index: Int = 0

        val done: Boolean get() = index >= text.length

        fun peek(): Char = text[index]

        fun take(): Char = text[index++]

        fun skipWhitespace() {
            while (!done && text[index].isWhitespace()) index++
        }

        /** Consumes [character] if it is there, and does nothing if it is not. */
        fun expect(character: Char) {
            if (!done && text[index] == character) index++
        }
    }
}
