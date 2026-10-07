package com.revisionapp.domain.model

/**
 * The five ways a card can be presented. A question is always *derived* from a
 * [Card] — there is no separate question entity — so one card can power every
 * mode.
 */
enum class StudyMode(val title: String, val difficultyRank: Int) {
    /** Flip, then self-rate Again / Hard / Good / Easy. */
    FLASHCARD("Flashcards", 0),

    /** Type the answer; graded by the answer checker, always overridable. */
    TYPED("Typed answer", 3),

    /** Order shuffled tiles to rebuild the answer (short answers only). */
    TILES("Word tiles", 2),

    /** Four options with immediate feedback and the explanation. */
    MCQ("Multiple choice", 1),

    /**
     * Picks the best-fit concrete mode per card. Always resolved to a concrete
     * mode before grading, so its rank is only a placeholder.
     */
    MIXED("Mixed", 0),
    ;

    /** Modes a session can actually present a card in. */
    val isConcrete: Boolean get() = this != MIXED

    companion object {
        val Concrete: List<StudyMode> = listOf(FLASHCARD, TYPED, TILES, MCQ)
        val All: List<StudyMode> = entries
    }
}

/**
 * How selected tags combine.
 *
 * - [ANY_WITHIN_GROUP] (default): tags inside the same [TagGroup] are OR-ed, and
 *   the groups are AND-ed, so "OCR" + "A Level" narrows the result.
 * - [ALL]: strict AND across every selected tag.
 */
enum class TagMatch {
    ANY_WITHIN_GROUP,
    ALL,
}

/**
 * Everything that narrows down which cards a browse list or a study session
 * sees. Filters are pure data: the same value is used for browsing and for
 * starting a session, which is what makes filters apply to sessions too.
 */
data class CardFilter(
    /** Selected topics; a selection always includes all descendants. Empty = all. */
    val topicIds: Set<TopicId> = emptySet(),
    val tagIds: Set<TagId> = emptySet(),
    val tagMatch: TagMatch = TagMatch.ANY_WITHIN_GROUP,
    val dueOnly: Boolean = false,
    val newOnly: Boolean = false,
    /** Null means both built-in and user content. */
    val source: ContentSource? = null,
) {
    val isUnfiltered: Boolean
        get() = topicIds.isEmpty() && tagIds.isEmpty() && !dueOnly && !newOnly && source == null

    fun withTopic(id: TopicId, selected: Boolean): CardFilter =
        copy(topicIds = if (selected) topicIds + id else topicIds - id)

    fun withTag(id: TagId, selected: Boolean): CardFilter =
        copy(tagIds = if (selected) tagIds + id else tagIds - id)

    companion object {
        val None: CardFilter = CardFilter()
    }
}

/** Applies a [CardFilter] to a card. Pure so it can be unit-tested directly. */
object CardFiltering {
    /**
     * @param selectedTopicIds the selected topics *expanded to include all
     *   descendants*, as produced by [TopicTree.selectedTopicIds].
     */
    fun matches(
        card: Card,
        filter: CardFilter,
        selectedTopicIds: Set<TopicId>,
        tagGroups: Map<TagId, TagGroup>,
        isDue: Boolean,
        isNew: Boolean,
    ): Boolean {
        if (filter.topicIds.isNotEmpty() && card.topicId !in selectedTopicIds) return false
        if (!matchesTags(card.tagIds, filter.tagIds, tagGroups, filter.tagMatch)) return false
        if (filter.dueOnly && !isDue) return false
        if (filter.newOnly && !isNew) return false
        val wantedSource = filter.source
        if (wantedSource != null && card.source != wantedSource) return false
        return true
    }

    fun matchesTags(
        cardTagIds: Set<TagId>,
        selectedTagIds: Set<TagId>,
        tagGroups: Map<TagId, TagGroup>,
        match: TagMatch,
    ): Boolean {
        if (selectedTagIds.isEmpty()) return true
        if (match == TagMatch.ALL) return cardTagIds.containsAll(selectedTagIds)
        // OR inside a group, AND across groups.
        return selectedTagIds
            .groupBy { tagGroups[it] }
            .values
            .all { group -> group.any { it in cardTagIds } }
    }
}
