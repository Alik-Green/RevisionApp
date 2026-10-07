package com.revisionapp.domain.usecase

import com.revisionapp.domain.model.CardFilter
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.TagGroup
import com.revisionapp.domain.model.TagId

/** One tag the filter sheet can offer, and what choosing it would do. */
data class ScopedTag(
    val tag: Tag,
    /** Cards that would match with this tag toggled from its current state. */
    val resultCount: Int,
    /** Cards in the current location carrying this tag, ignoring the tag filter. */
    val availableCount: Int,
    val isSelected: Boolean,
) {
    /** A chip that would empty the list is shown dead rather than offered. */
    val wouldGiveResults: Boolean get() = resultCount > 0
}

/** One tag group in the filter sheet, e.g. every `board` tag that applies here. */
data class TagGroupOptions(val group: TagGroup, val tags: List<ScopedTag>)

/**
 * Which filters are valid *here*.
 *
 * The Library offers only the tags that occur on at least one card inside the
 * current location, descendants included, each with a count — so a Physics topic
 * never offers `subject = Further Maths`, and a chip that would produce an empty
 * list is visibly dead instead of being a surprise. Counts are recomputed with the
 * rest of the filter held fixed, so selecting one tag immediately re-prices the
 * others.
 *
 * Pure over the snapshot: no database, so the OR-within-group / AND-across-groups
 * rule can be tested directly. See docs/DECISIONS.md D35.
 */
object ScopedFilters {

    /** At this size a chip row stays readable; beyond it the sheet scrolls. */
    fun tagGroups(library: LibrarySnapshot, filter: CardFilter): List<TagGroupOptions> {
        val inScope = library.filtered(filter.copy(tagIds = emptySet()))
        if (inScope.isEmpty()) return emptyList()

        val available = HashMap<TagId, Int>()
        for (card in inScope) {
            for (tagId in card.tagIds) {
                available[tagId] = (available[tagId] ?: 0) + 1
            }
        }

        return library.tags
            .filter { available.containsKey(it.id) }
            .groupBy { it.group }
            .map { (group, tags) ->
                TagGroupOptions(
                    group = group,
                    tags = tags
                        .map { tag -> scopedTag(library, filter, tag, available.getValue(tag.id)) }
                        .sortedWith(
                            compareByDescending<ScopedTag> { it.availableCount }.thenBy { it.tag.name },
                        ),
                )
            }
            .sortedBy { it.group.value }
    }

    private fun scopedTag(
        library: LibrarySnapshot,
        filter: CardFilter,
        tag: Tag,
        availableCount: Int,
    ): ScopedTag {
        val selected = tag.id in filter.tagIds
        val toggled = if (selected) filter.tagIds - tag.id else filter.tagIds + tag.id
        return ScopedTag(
            tag = tag,
            resultCount = library.filtered(filter.copy(tagIds = toggled)).size,
            availableCount = availableCount,
            isSelected = selected,
        )
    }
}
