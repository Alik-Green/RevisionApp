package com.revisionapp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardFilteringTest {

    private val boardOcr = TagId("builtin:tag:board:ocr")
    private val boardEdexcel = TagId("builtin:tag:board:edexcel")
    private val levelALevel = TagId("builtin:tag:level:a-level")
    private val levelGcse = TagId("builtin:tag:level:gcse")
    private val subjectPhysics = TagId("builtin:tag:subject:physics")

    private val tagGroups: Map<TagId, TagGroup> = mapOf(
        boardOcr to TagGroup.Board,
        boardEdexcel to TagGroup.Board,
        levelALevel to TagGroup.Level,
        levelGcse to TagGroup.Level,
        subjectPhysics to TagGroup.Subject,
    )

    private fun card(
        id: String,
        topic: String = "circular-motion",
        tags: Set<TagId> = emptySet(),
        source: ContentSource = ContentSource.BUILTIN,
    ): Card = Card(
        id = CardId(id),
        topicId = TopicId(topic),
        tagIds = tags,
        front = "front",
        back = "back",
        source = source,
    )

    private fun matches(
        card: Card,
        filter: CardFilter,
        selectedTopics: Set<TopicId> = filter.topicIds,
        isDue: Boolean = true,
        isNew: Boolean = false,
    ): Boolean = CardFiltering.matches(
        card = card,
        filter = filter,
        selectedTopicIds = selectedTopics,
        tagGroups = tagGroups,
        isDue = isDue,
        isNew = isNew,
    )

    @Test
    fun anEmptyFilterAcceptsEverything() {
        assertTrue(matches(card("a"), CardFilter.None))
    }

    @Test
    fun tagsInTheSameGroupCombineWithOr() {
        val filter = CardFilter(tagIds = setOf(boardOcr, boardEdexcel))
        val ocrCard = card("ocr", tags = setOf(boardOcr))
        val edexcelCard = card("edexcel", tags = setOf(boardEdexcel))
        val untagged = card("untagged")

        assertTrue(matches(ocrCard, filter))
        assertTrue(matches(edexcelCard, filter))
        assertFalse(matches(untagged, filter))
    }

    @Test
    fun tagsInDifferentGroupsCombineWithAnd() {
        val filter = CardFilter(tagIds = setOf(boardOcr, levelALevel))

        assertTrue(matches(card("both", tags = setOf(boardOcr, levelALevel)), filter))
        // "OCR" + "GCSE" must narrow the result away from an A Level card.
        assertFalse(matches(card("ocrOnly", tags = setOf(boardOcr)), filter))
        assertFalse(matches(card("gcse", tags = setOf(boardOcr, levelGcse)), filter))
    }

    @Test
    fun threeGroupsAllHaveToMatch() {
        val filter = CardFilter(tagIds = setOf(boardOcr, levelALevel, subjectPhysics))

        assertTrue(matches(card("all", tags = setOf(boardOcr, levelALevel, subjectPhysics)), filter))
        assertFalse(matches(card("two", tags = setOf(boardOcr, levelALevel)), filter))
    }

    @Test
    fun matchAllRequiresEverySelectedTagEvenInsideOneGroup() {
        val both = setOf(boardOcr, boardEdexcel)
        val filter = CardFilter(tagIds = both, tagMatch = TagMatch.ALL)

        assertTrue(matches(card("both", tags = both), filter))
        assertFalse(matches(card("ocrOnly", tags = setOf(boardOcr)), filter))
    }

    @Test
    fun topicFilterUsesTheExpandedSelection() {
        val filter = CardFilter(topicIds = setOf(TopicId("module-4")))
        val expanded = setOf(TopicId("module-4"), TopicId("circular-motion"))

        assertTrue(matches(card("inside", topic = "circular-motion"), filter, expanded))
        assertFalse(matches(card("outside", topic = "module-1"), filter, expanded))
    }

    @Test
    fun dueOnlyAndNewOnlyAreIndependent() {
        val dueCard = card("a")
        assertTrue(matches(dueCard, CardFilter(dueOnly = true), isDue = true))
        assertFalse(matches(dueCard, CardFilter(dueOnly = true), isDue = false))

        assertTrue(matches(dueCard, CardFilter(newOnly = true), isNew = true))
        assertFalse(matches(dueCard, CardFilter(newOnly = true), isNew = false))
    }

    @Test
    fun sourceFilterSeparatesBuiltInFromUserContent() {
        val builtIn = card("builtin", source = ContentSource.BUILTIN)
        val user = card("user", source = ContentSource.USER)

        assertTrue(matches(builtIn, CardFilter(source = ContentSource.BUILTIN)))
        assertFalse(matches(user, CardFilter(source = ContentSource.BUILTIN)))
        assertTrue(matches(user, CardFilter(source = ContentSource.USER)))
        assertTrue(matches(user, CardFilter(source = null)))
    }

    @Test
    fun everyCriterionHasToPassAtOnce() {
        val filter = CardFilter(
            topicIds = setOf(TopicId("circular-motion")),
            tagIds = setOf(boardOcr),
            dueOnly = true,
            source = ContentSource.BUILTIN,
        )
        val card = card("a", topic = "circular-motion", tags = setOf(boardOcr))

        assertTrue(matches(card, filter, setOf(TopicId("circular-motion")), isDue = true))
        assertFalse(matches(card, filter, setOf(TopicId("circular-motion")), isDue = false))
        assertFalse(matches(card.copy(topicId = TopicId("elsewhere")), filter, setOf(TopicId("circular-motion"))))
    }

    @Test
    fun withTopicAndWithTagToggleMembership() {
        val start = CardFilter.None
        val withTopic = start.withTopic(TopicId("a"), selected = true)
        assertEquals(setOf(TopicId("a")), withTopic.topicIds)

        val withoutTopic = withTopic.withTopic(TopicId("a"), selected = false)
        assertTrue(withoutTopic.topicIds.isEmpty())
        assertTrue(withoutTopic.isUnfiltered)

        val withTag = withoutTopic.withTag(boardOcr, selected = true)
        assertFalse(withTag.isUnfiltered)
        assertEquals(setOf(boardOcr), withTag.tagIds)
    }
}
