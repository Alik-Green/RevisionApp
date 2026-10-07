package com.revisionapp.domain.check

/**
 * A very light suffix stripper. It is not Porter — it only has to make
 * `increase` / `increases` / `increased` / `increasing` collapse to the same
 * token while keeping `increase` and `decrease` distinct, because the checker
 * compares two stemmed token sets with [EditDistance].
 */
object Stemmer {

    fun stem(token: String): String {
        var result = token.lowercase()
        if (result.length <= 2) return result

        // Note: no `ies -> y` rule. It would turn "series" into "sery" and
        // "species" into "specy"; leaving those alone and relying on the fuzzy
        // token comparison for energy/energies is strictly safer.
        if (result.length > 3 && result.endsWith("s") &&
            !result.endsWith("ss") && !result.endsWith("us") && !result.endsWith("is")
        ) {
            result = result.dropLast(1)
        }

        result = when {
            result.length > 5 && result.endsWith("ing") -> result.dropLast(3)
            result.length > 4 && result.endsWith("ed") && !result.endsWith("eed") -> result.dropLast(2)
            result.length > 4 && result.endsWith("ly") -> result.dropLast(2)
            else -> result
        }

        if (result.length > 4 && result.endsWith("e")) {
            result = result.dropLast(1)
        }
        return result
    }

    fun stemAll(tokens: Iterable<String>): List<String> = tokens.map { stem(it) }
}
