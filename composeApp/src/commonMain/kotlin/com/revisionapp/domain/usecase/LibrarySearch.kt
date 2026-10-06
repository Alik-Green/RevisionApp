package com.revisionapp.domain.usecase

import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardFilter
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.Topic

/**
 * One group of search results, in the order the results screen shows them.
 *
 * A sealed type rather than a map of titles to lists so that adding a `Notes`
 * section later is one case here plus the `when` branches the compiler then
 * demands, not a change to how every caller iterates results.
 */
sealed interface SearchSection {
    val title: String

    data class Topics(val items: List<Topic>) : SearchSection {
        override val title: String get() = "Topics"
    }

    data class Tags(val items: List<Tag>) : SearchSection {
        override val title: String get() = "Tags"
    }

    data class Cards(val items: List<Card>) : SearchSection {
        override val title: String get() = "Cards"
    }
}

/**
 * Case- and diacritic-insensitive search over the loaded snapshot: topic names,
 * tag names and groups, card fronts, backs, key points with their synonyms, and
 * explanations.
 *
 * It is a scan rather than a query because the snapshot already holds every topic,
 * tag and card in memory. That means there is no FTS index to keep in step with a
 * content sync and nothing to rebuild after an import — the two ways an index
 * quietly goes stale — and at this library's size a scan beats the round trip.
 * See docs/DECISIONS.md D35.
 *
 * Cards are searched within [scope], so results respect the active filters; pass
 * [CardFilter.None] to search everywhere.
 */
object LibrarySearch {

    /** Enough to be useful, small enough that the results screen stays scannable. */
    const val MAX_RESULTS_PER_SECTION: Int = 40

    fun search(library: LibrarySnapshot, query: String, scope: CardFilter = CardFilter.None): List<SearchSection> {
        val needle = fold(query)
        if (needle.isEmpty()) return emptyList()

        val sections = ArrayList<SearchSection>(3)

        val topics = library.topics.filter { needle in fold(it.name) }
            .sortedBy { it.sortOrder }
            .take(MAX_RESULTS_PER_SECTION)
        if (topics.isNotEmpty()) sections += SearchSection.Topics(topics)

        val tags = library.tags.filter { needle in fold(it.name) || needle in fold(it.group.value) }
            .sortedBy { it.name }
            .take(MAX_RESULTS_PER_SECTION)
        if (tags.isNotEmpty()) sections += SearchSection.Tags(tags)

        val cards = library.filtered(scope).filter { matches(it, needle) }
            .take(MAX_RESULTS_PER_SECTION)
        if (cards.isNotEmpty()) sections += SearchSection.Cards(cards)

        return sections
    }

    private fun matches(card: Card, needle: String): Boolean {
        if (needle in fold(card.front) || needle in fold(card.back)) return true
        if (card.keyPoints.any { point ->
                needle in fold(point.text) || point.synonyms.any { needle in fold(it) }
            }
        ) {
            return true
        }
        val explanation = card.explanation
        return explanation != null && needle in fold(explanation)
    }

    /** The text around a match, for the results screen's snippet. */
    fun snippet(source: String, query: String, radius: Int = 48): String {
        val needle = fold(query)
        if (needle.isEmpty()) return source.take(radius * 2)
        val folded = fold(source)
        val index = folded.indexOf(needle)
        if (index < 0) return source.take(radius * 2)
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + needle.length + radius).coerceAtMost(source.length)
        val prefix = if (start > 0) "\u2026" else ""
        val suffix = if (end < source.length) "\u2026" else ""
        return prefix + source.substring(start, end).trim() + suffix
    }

    private val DIACRITICS: Map<Char, String> = mapOf(
        '\u00E0' to "a", '\u00E1' to "a", '\u00E2' to "a", '\u00E3' to "a", '\u00E4' to "a", '\u00E5' to "a",
        '\u00E8' to "e", '\u00E9' to "e", '\u00EA' to "e", '\u00EB' to "e",
        '\u00EC' to "i", '\u00ED' to "i", '\u00EE' to "i", '\u00EF' to "i",
        '\u00F2' to "o", '\u00F3' to "o", '\u00F4' to "o", '\u00F5' to "o", '\u00F6' to "o", '\u00F8' to "o",
        '\u00F9' to "u", '\u00FA' to "u", '\u00FB' to "u", '\u00FC' to "u",
        '\u00F1' to "n", '\u00E7' to "c", '\u00FD' to "y", '\u00FF' to "y",
        '\u00E6' to "ae", '\u0153' to "oe", '\u00DF' to "ss", '\u00F0' to "d", '\u00FE' to "th", '\u0142' to "l",
    )

    /** Lowercases, folds Latin diacritics and collapses whitespace. */
    fun fold(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val builder = StringBuilder(trimmed.length)
        for (character in trimmed.lowercase()) {
            builder.append(DIACRITICS[character] ?: character)
        }
        return WHITESPACE.replace(builder.toString(), " ")
    }

    private val WHITESPACE = Regex("""\s+""")
}
