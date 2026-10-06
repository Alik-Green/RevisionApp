package com.revisionapp.math

/**
 * Unicode tables for the maths renderer.
 *
 * Commands are keyed by *name without the backslash*, because the parser consumes
 * `\alpha` as one token. The previous renderer did ordered substring replacement
 * over the whole string, which is why `\leq` had to be listed before `\le` and
 * `\Rightarrow` could be missed entirely and fall through to "print the command
 * name". A map lookup on a parsed name has no such hazard.
 */
object MathUnicode {

    /**
     * `^`/`_` bodies that consist only of these characters become real Unicode
     * superscripts and subscripts. Anything else is left to the renderer, which
     * draws it as smaller, baseline-shifted text — so no expression can turn into
     * tofu just because one character has no Unicode equivalent.
     */
    private val SUPERSCRIPTS: Map<Char, Char> = mapOf(
        '0' to '\u2070', '1' to '\u00B9', '2' to '\u00B2', '3' to '\u00B3', '4' to '\u2074',
        '5' to '\u2075', '6' to '\u2076', '7' to '\u2077', '8' to '\u2078', '9' to '\u2079',
        '+' to '\u207A', '-' to '\u207B', '=' to '\u207C', '(' to '\u207D', ')' to '\u207E',
        'n' to '\u207F', 'i' to '\u2071',
    )

    private val SUBSCRIPTS: Map<Char, Char> = mapOf(
        '0' to '\u2080', '1' to '\u2081', '2' to '\u2082', '3' to '\u2083', '4' to '\u2084',
        '5' to '\u2085', '6' to '\u2086', '7' to '\u2087', '8' to '\u2088', '9' to '\u2089',
        '+' to '\u208A', '-' to '\u208B', '=' to '\u208C', '(' to '\u208D', ')' to '\u208E',
        'a' to '\u2090', 'e' to '\u2091', 'h' to '\u2095', 'k' to '\u2096', 'l' to '\u2097',
        'm' to '\u2098', 'n' to '\u2099', 'o' to '\u2092', 'p' to '\u209A', 's' to '\u209B',
        't' to '\u209C', 'i' to '\u1D62', 'r' to '\u1D63', 'u' to '\u1D64', 'v' to '\u1D65',
        'x' to '\u2093',
    )

    /**
     * Every character of [body] as a superscript, or null when even one has no
     * Unicode equivalent. All-or-nothing on purpose: `xⁿ¹` mixed with a literal
     * `+1` would look worse than a consistently styled fallback.
     */
    fun superscript(body: String): String? {
        if (body.isEmpty()) return null
        val builder = StringBuilder(body.length)
        for (character in body) {
            val mapped = SUPERSCRIPTS[character] ?: return null
            builder.append(mapped)
        }
        return builder.toString()
    }

    /** Every character of [body] as a subscript, or null if any is unmappable. */
    fun subscript(body: String): String? {
        if (body.isEmpty()) return null
        val builder = StringBuilder(body.length)
        for (character in body) {
            val mapped = SUBSCRIPTS[character] ?: return null
            builder.append(mapped)
        }
        return builder.toString()
    }

    /**
     * LaTeX command name to the text it should become. Arrows, relations, logic,
     * operators, both Greek cases, and the function names that are set upright.
     */
    val COMMANDS: Map<String, String> = mapOf(
        // Arrows.
        "longrightarrow" to "\u27F6",
        "longleftarrow" to "\u27F5",
        "Leftrightarrow" to "\u21D4",
        "leftrightarrow" to "\u2194",
        "Rightarrow" to "\u21D2",
        "Leftarrow" to "\u21D0",
        "rightarrow" to "\u2192",
        "leftarrow" to "\u2190",
        "implies" to "\u27F9",
        "impliedby" to "\u27F8",
        "uparrow" to "\u2191",
        "downarrow" to "\u2193",
        "mapsto" to "\u21A6",
        "iff" to "\u27FA",
        "to" to "\u2192",
        // Relations.
        "subseteq" to "\u2286",
        "supseteq" to "\u2287",
        "propto" to "\u221D",
        "approx" to "\u2248",
        "equiv" to "\u2261",
        "subset" to "\u2282",
        "supset" to "\u2283",
        "notin" to "\u2209",
        "neq" to "\u2260",
        "leq" to "\u2264",
        "geq" to "\u2265",
        "sim" to "\u223C",
        "simeq" to "\u2243",
        "cong" to "\u2245",
        "perp" to "\u22A5",
        "parallel" to "\u2225",
        "ne" to "\u2260",
        "le" to "\u2264",
        "ge" to "\u2265",
        "ll" to "\u226A",
        "gg" to "\u226B",
        "in" to "\u2208",
        "ni" to "\u220B",
        // Logic and quantifiers.
        "therefore" to "\u2234",
        "because" to "\u2235",
        "nexists" to "\u2204",
        "exists" to "\u2203",
        "forall" to "\u2200",
        "wedge" to "\u2227",
        "vee" to "\u2228",
        "lnot" to "\u00AC",
        "land" to "\u2227",
        "lor" to "\u2228",
        "neg" to "\u00AC",
        "top" to "\u22A4",
        "bot" to "\u22A5",
        // Operators.
        "times" to "\u00D7",
        "cdot" to "\u00B7",
        "cdots" to "\u22EF",
        "ldots" to "\u2026",
        "dots" to "\u2026",
        "vdots" to "\u22EE",
        "ddots" to "\u22F1",
        "setminus" to "\u2216",
        "otimes" to "\u2297",
        "oplus" to "\u2295",
        "ast" to "\u2217",
        "star" to "\u22C6",
        "circ" to "\u2218",
        "div" to "\u00F7",
        "pm" to "\u00B1",
        "mp" to "\u2213",
        // Calculus and big operators.
        "infty" to "\u221E",
        "partial" to "\u2202",
        "nabla" to "\u2207",
        "oint" to "\u222E",
        "iint" to "\u222C",
        "prod" to "\u220F",
        "sum" to "\u2211",
        "int" to "\u222B",
        "lim" to "lim",
        // Sets and geometry.
        "varnothing" to "\u2205",
        "emptyset" to "\u2205",
        "degree" to "\u00B0",
        "angle" to "\u2220",
        "mathbb" to "",
        "quad" to " ",
        "qquad" to "  ",
        // Greek, lower case.
        "varepsilon" to "\u03B5",
        "vartheta" to "\u03D1",
        "varsigma" to "\u03C2",
        "varphi" to "\u03C6",
        "varrho" to "\u03F1",
        "varpi" to "\u03D6",
        "alpha" to "\u03B1",
        "beta" to "\u03B2",
        "gamma" to "\u03B3",
        "delta" to "\u03B4",
        "epsilon" to "\u03B5",
        "zeta" to "\u03B6",
        "eta" to "\u03B7",
        "theta" to "\u03B8",
        "iota" to "\u03B9",
        "kappa" to "\u03BA",
        "lambda" to "\u03BB",
        "mu" to "\u03BC",
        "nu" to "\u03BD",
        "xi" to "\u03BE",
        "pi" to "\u03C0",
        "rho" to "\u03C1",
        "sigma" to "\u03C3",
        "tau" to "\u03C4",
        "upsilon" to "\u03C5",
        "phi" to "\u03C6",
        "chi" to "\u03C7",
        "psi" to "\u03C8",
        "omega" to "\u03C9",
        // Greek, upper case.
        "Gamma" to "\u0393",
        "Delta" to "\u0394",
        "Theta" to "\u0398",
        "Lambda" to "\u039B",
        "Xi" to "\u039E",
        "Pi" to "\u03A0",
        "Sigma" to "\u03A3",
        "Upsilon" to "\u03A5",
        "Phi" to "\u03A6",
        "Psi" to "\u03A8",
        "Omega" to "\u03A9",
        // Function names, set upright.
        "cosec" to "cosec",
        "arcsin" to "arcsin",
        "arccos" to "arccos",
        "arctan" to "arctan",
        "sinh" to "sinh",
        "cosh" to "cosh",
        "tanh" to "tanh",
        "coth" to "coth",
        "sech" to "sech",
        "csch" to "csch",
        "log" to "log",
        "sin" to "sin",
        "cos" to "cos",
        "tan" to "tan",
        "cot" to "cot",
        "sec" to "sec",
        "exp" to "exp",
        "det" to "det",
        "gcd" to "gcd",
        "max" to "max",
        "min" to "min",
        "sup" to "sup",
        "inf" to "inf",
        "arg" to "arg",
        "deg" to "deg",
        "dim" to "dim",
        "ker" to "ker",
        "hom" to "hom",
        "mod" to "mod",
        "Pr" to "Pr",
        "ln" to "ln",
        "lg" to "lg",
    )

    /** Single-character spacing and escape commands: `\,`, `\;`, `\:`, `\!`, `\%`. */
    val SPACING: Map<Char, String> = mapOf(
        ',' to " ",
        ';' to " ",
        ':' to " ",
        '!' to "",
        '%' to "%",
        '$' to "$",
        '&' to "&",
        '#' to "#",
        '_' to "_",
        '{' to "{",
        '}' to "}",
        '\\' to "\\",
    )

    /**
     * Commands that take one braced argument whose contents should be set as
     * ordinary upright text, not as maths.
     */
    val TEXT_COMMANDS: Set<String> = setOf(
        "text", "mathrm", "mathbf", "mathit", "mbox", "operatorname", "textrm", "textbf",
    )

    /** Commands that decorate a single argument and are approximated by it. */
    val DECORATION_COMMANDS: Set<String> = setOf(
        "vec", "hat", "bar", "dot", "ddot", "overline", "underline", "widehat", "widetilde", "tilde",
    )

    /** Layout commands this parser consumes and discards. */
    val IGNORED_COMMANDS: Set<String> = setOf(
        "left", "right", "displaystyle", "textstyle", "scriptstyle", "limits", "nonumber", "centering",
    )
}
