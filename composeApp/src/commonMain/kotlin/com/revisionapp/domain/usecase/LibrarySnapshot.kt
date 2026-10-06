package com.revisionapp.domain.usecase

import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardFilter
import com.revisionapp.domain.model.CardFiltering
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.TagGroup
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.Topic
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.model.TopicTree
import com.revisionapp.domain.model.TopicTreeBuilder
import com.revisionapp.domain.repository.LibraryRepository
import com.revisionapp.domain.repository.ProgressRepository
import com.revisionapp.domain.repository.TopicAccuracy
import com.revisionapp.domain.srs.ScheduleState
import kotlinx.datetime.Instant

/**
 * An immutable view of the whole library at one instant: the topic tree with its
 * counts, every card, every tag and the schedule state that makes "due" and "new"
 * meaningful.
 *
 * Screens read this and nothing else, so filters, counts and session contents can
 * never disagree about what "due" means.
 */
data class LibrarySnapshot(
    val topics: List<Topic>,
    val tags: List<Tag>,
    val cards: List<Card>,
    val states: Map<CardId, ScheduleState>,
    val now: Instant,
    val tree: TopicTree,
) {
    val tagGroups: Map<TagId, TagGroup> = tags.associate { it.id to it.group }

    fun stateOf(cardId: CardId): ScheduleState = states[cardId] ?: ScheduleState.new(now)

    fun isDue(card: Card): Boolean = stateOf(card.id).isDue(now)

    fun isNew(card: Card): Boolean = stateOf(card.id).isNew

    fun siblingsOf(card: Card): List<Card> = cardsByTopic[card.topicId].orEmpty().filter { it.id != card.id }

    /** Cards matching [filter], in a stable order (topic order, then card id). */
    fun filtered(filter: CardFilter): List<Card> {
        val selectedTopics = tree.selectedTopicIds(filter.topicIds)
        return cards
            .filter { CardFiltering.matches(it, filter, selectedTopics, tagGroups, isDue(it), isNew(it)) }
            .sortedWith(compareBy({ topicRank(it.topicId) }, { it.id.value }))
    }

    /** Cards that are due right now, including cards that have never been seen. */
    fun dueCards(filter: CardFilter = CardFilter.None): List<Card> =
        filtered(filter).filter { isDue(it) }

    fun dueCount(filter: CardFilter = CardFilter.None): Int = dueCards(filter).size

    fun cardById(id: CardId): Card? = cards.firstOrNull { it.id == id }

    /** How many cards are due by the end of [today], for the stats screen. */
    fun dueByEndOfDay(endOfDay: Instant): Int =
        cards.count { stateOf(it.id).dueAt <= endOfDay }

    private val cardsByTopic: Map<TopicId, List<Card>> = cards.groupBy { it.topicId }

    private val topicOrder: Map<TopicId, Int> = tree.flatten().withIndex().associate { (index, node) -> node.id to index }

    private fun topicRank(topicId: TopicId): Int = topicOrder[topicId] ?: Int.MAX_VALUE

    companion object {
        val Empty: LibrarySnapshot = LibrarySnapshot(
            topics = emptyList(),
            tags = emptyList(),
            cards = emptyList(),
            states = emptyMap(),
            now = Instant.DISTANT_PAST,
            tree = TopicTree.Empty,
        )
    }
}

/** Reads the repositories and builds a [LibrarySnapshot]. */
class StudyPlanner(
    private val library: LibraryRepository,
    private val progress: ProgressRepository,
) {
    fun snapshot(now: Instant): LibrarySnapshot {
        val topics = library.topics()
        val cards = library.cards()
        val states = progress.states()

        val cardCounts = cards.groupingBy { it.topicId }.eachCount()
        val dueCounts = HashMap<TopicId, Int>()
        for (card in cards) {
            val state = states[card.id] ?: ScheduleState.new(now)
            if (state.isDue(now)) {
                dueCounts[card.topicId] = (dueCounts[card.topicId] ?: 0) + 1
            }
        }

        return LibrarySnapshot(
            topics = topics,
            tags = library.tags(),
            cards = cards,
            states = states,
            now = now,
            tree = TopicTreeBuilder.build(topics, cardCounts, dueCounts),
        )
    }

    fun accuracyByTopic(): Map<TopicId, TopicAccuracy> = progress.accuracyByTopic()
}
