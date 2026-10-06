package com.revisionapp.math

/**
 * The parsed form of a piece of mathematics.
 *
 * This exists because the previous renderer did string replacement, which cannot
 * answer the only question that actually matters: "does every character in this
 * script have a Unicode equivalent, or does it need a baseline shift?" That needs
 * a tree, not a regex. See docs/DECISIONS.md D32.
 *
 * The AST is pure Kotlin with no Compose imports, so the parser and both
 * renderers are unit-testable on every target.
 */
sealed interface MathNode {

    /** Text that needs no layout of its own: prose, variables, mapped symbols. */
    data class Run(val text: String) : MathNode

    /** A `{...}` group, kept whole so a script can attach to all of it. */
    data class Group(val children: List<MathNode>) : MathNode

    /**
     * A base with a superscript and/or subscript. Both are nullable because
     * `x^2` and `x_i` each supply only one, and `x_i^2` supplies both.
     */
    data class Script(
        val base: List<MathNode>,
        val sup: List<MathNode>?,
        val sub: List<MathNode>?,
    ) : MathNode

    /** `\frac{a}{b}`, drawn stacked by the Compose renderer. */
    data class Fraction(val numerator: List<MathNode>, val denominator: List<MathNode>) : MathNode

    /** `\sqrt{x}` or `\sqrt[n]{x}`. */
    data class Radical(val index: List<MathNode>?, val body: List<MathNode>) : MathNode

    /**
     * A command this parser does not know. Rendered as its name so an unsupported
     * expression degrades to readable text instead of showing a raw backslash or
     * throwing.
     */
    data class Unknown(val name: String) : MathNode

    companion object {
        /** The nodes of an empty expression. */
        val Empty: List<MathNode> = emptyList()
    }
}
