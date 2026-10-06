package com.revisionapp.domain.usecase

import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardFilter
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.ContentSource
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.TagGroup
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.Topic
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.model.TopicTreeBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Scoped filters and search: the two pieces of logic the Library screen is built
 * on. Both are pure over a snapshot, so neither needs a database to be tested.
 */
class ScopedFiltersAndSearchTest {

    private val now: Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private val physics = TopicId("builtin:p:physics")
    private val circular = TopicId("builtin:p:circular-motion")
    private val maths = TopicId("builtin:m:maths")

    private val ocr = TagId("builtin:tag:board:ocr")
    private val edexcel = TagId("builtin:tag:board:edexcel")
    private val subjectPhysics = TagId("builtin:tag:subject:physics")
    private val subjectMaths = TagId("builtin:tag:subject:maths")

    private val topics = listOf(
        Topic(physics, "Physics", null, 0, ContentSource.BUILTIN, null),
        Topic(circular, "Circular motion", physics, 1, ContentSource.BUILTIN, null),
        Topic(maths, "Maths", null, 0, ContentSource.BUILTIN, null),
    )

    private val tags = listOf(
        Tag(ocr, "OCR", TagGroup.Board, ContentSource.BUILTIN),
        Tag(edexcel, "Edexcel", TagGroup.Board, ContentSource.BUILTIN),
        Tag(subjectPhysics, "Physics", TagGroup.Subject, ContentSource.BUILTIN),
        Tag(subjectMaths, "Maths", TagGroup.Subject, ContentSource.BUILTIN),
    )

    private fun card(id: String, topic: TopicId, tagIds: Set<TagId>, front: String, back: String): Card = Card(
        id = CardId(id),
        topicId = topic,
        tagIds = tagIds,
        front = front,
        back = back,
        source = ContentSource.BUILTIN,
    )

    private val cards = listOf(
        card("c1", circular, setOf(ocr, subjectPhysics), "What is centripetal force?", "Towards the centre"),
        card("c2", circular, setOf(ocr, subjectPhysics), "Define angular velocity", "Rate of change of angle"),
        card("c3", physics, setOf(edexcel, subjectPhysics), "Résumé of energy stores", "Kinetic and potential"),
        card("c4", maths, setOf(edexcel, subjectMaths), "Define an eigenvalue", "Ax equals lambda x"),
    )

    private val library = LibrarySnapshot(
        topics = topics,
        tags = tags,
        cards = cards,
        states = emptyMap(),
        now = now,
        tree = TopicTreeBuilder.build(topics, cards.groupingBy { it.topicId }.eachCount()),
    )

    /** The filter the Library holds while sitting inside [location]. */
    private fun at(location: TopicId?, tagIds: Set<TagId> = emptySet()): CardFilter = CardFilter(
        topicIds = if (location == null) emptySet() else library.tree.selectedTopicIds(setOf(location)),
        tagIds = tagIds,
    )

    // -------------------------------------------------- scoped filters ---

    @Test
    fun onlyTagsThatOccurHereAreOffered() {
        val offered = ScopedFilters.tagGroups(library, at(physics))
            .flatMap { group -> group.tags }
            .map { it.tag.id }
            .toSet()

        assertTrue(ocr in offered)
        assertTrue(edexcel in offered)
        assertTrue(subjectPhysics in offered)
        // Nothing in the Physics subtree is tagged as Maths, so offering it would
        // be a chip that can only ever produce an empty list.
        assertFalse(subjectMaths in offered)
    }

    @Test
    fun aLocationIncludesItsDescendants() {
        val offered = ScopedFilters.tagGroups(library, at(physics))
            .flatMap { group -> group.tags }
        val physicsCount = offered.first { it.tag.id == subjectPhysics }.availableCount

        // c1 and c2 sit in the sub-topic, c3 in Physics itself: all three are here.
        assertEquals(3, physicsCount)
    }

    @Test
    fun countsAreRecomputedWithTheRestOfTheFilterHeldFixed() {
        val groups = ScopedFilters.tagGroups(library, at(physics, setOf(ocr)))
        val selected = groups.flatMap { it.tags }.first { it.tag.id == ocr }
        val other = groups.flatMap { it.tags }.first { it.tag.id == edexcel }

        assertTrue(selected.isSelected)
        // Deselecting OCR removes the tag filter altogether, so all three cards in
        // this subtree come back: c1 and c2 were OCR, c3 was Edexcel.
        assertEquals(3, selected.resultCount)
        assertFalse(other.isSelected)
        // Adding Edexcel beside OCR is an OR within the board group.
        assertEquals(3, other.resultCount)
        assertTrue(other.wouldGiveResults)
    }

    @Test
    fun aChipThatWouldEmptyTheListIsMarkedDead() {
        val groups = ScopedFilters.tagGroups(library, at(null, setOf(ocr)))
        val mathsSubject = groups.flatMap { it.tags }.first { it.tag.id == subjectMaths }

        // AND across groups: nothing is both OCR and Maths.
        assertEquals(0, mathsSubject.resultCount)
        assertFalse(mathsSubject.wouldGiveResults)
    }

    @Test
    fun groupsAreOfferedInGroupOrderAndTagsByHowCommonTheyAre() {
        val groups = ScopedFilters.tagGroups(library, at(null))

        assertEquals(listOf("board", "subject"), groups.map { it.group.value })
        val board = groups.first().tags
        assertEquals(2, board.first().availableCount)
        assertEquals(2, board.last().availableCount)
    }

    @Test
    fun anEmptyScopeOffersNoFiltersAtAll() {
        val nothingMatches = at(null).copy(source = ContentSource.USER)

        assertEquals(emptyList(), ScopedFilters.tagGroups(library, nothingMatches))
    }

    // ----------------------------------------------------------- search ---

    @Test
    fun resultsAreGroupedByType() {
        val sections = LibrarySearch.search(library, "motion")

        assertEquals(listOf("Topics"), sections.map { it.title })
        val topics = (sections.single() as SearchSection.Topics).items
        assertEquals(listOf(circular), topics.map { it.id })
    }

    @Test
    fun cardsAreFoundByFrontBackKeyPointsAndExplanation() {
        assertEquals(1, LibrarySearch.search(library, "centripetal").let { it.size })
        assertTrue(LibrarySearch.search(library, "towards the centre").isNotEmpty())
        assertTrue(LibrarySearch.search(library, "lambda").isNotEmpty())
    }

    @Test
    fun searchIgnoresCaseAndDiacritics() {
        assertEquals("resume", LibrarySearch.fold("Résumé"))
        assertTrue(LibrarySearch.search(library, "RESUME").isNotEmpty())
        assertTrue(LibrarySearch.search(library, "resume").isNotEmpty())
        assertTrue(LibrarySearch.search(library, "ANGULAR").isNotEmpty())
    }

    @Test
    fun tagsAreFoundByNameAndByGroup() {
        val byName = LibrarySearch.search(library, "edexcel").single()
        assertEquals("Tags", byName.title)
        assertTrue(LibrarySearch.search(library, "board").isNotEmpty())
    }

    @Test
    fun searchRespectsTheActiveScope() {
        val everywhere = LibrarySearch.search(library, "card", CardFilter.None)
        val inMaths = LibrarySearch.search(library, "eigenvalue", at(maths))
        val outsideMaths = LibrarySearch.search(library, "eigenvalue", at(physics))

        assertTrue(everywhere.isEmpty(), "no card text contains the word 'card'")
        assertTrue(inMaths.isNotEmpty())
        assertTrue(outsideMaths.isEmpty())
    }

    @Test
    fun anEmptyOrBlankQueryReturnsNothing() {
        assertEquals(emptyList(), LibrarySearch.search(library, ""))
        assertEquals(emptyList(), LibrarySearch.search(library, "   "))
    }

    @Test
    fun resultsAreCapped() {
        val many = (1..120).map { index ->
            card("bulk$index", physics, emptySet(), "needle number $index", "back")
        }
        val bulk = library.copy(cards = library.cards + many)

        val cards = LibrarySearch.search(bulk, "needle").map { it as SearchSection.Cards }.single()
        assertEquals(LibrarySearch.MAX_RESULTS_PER_SECTION, cards.items.size)
    }

    @Test
    fun aSnippetCentresOnTheMatchAndMarksElision() {
        val source = "The centripetal force on a body moving in a circle points towards the centre."
        val snippet = LibrarySearch.snippet(source, "centripetal", radius = 10)

        assertTrue(snippet.contains("centripetal"))
        assertTrue(snippet.endsWith("\u2026"))
        assertFalse(snippet.startsWith("\u2026"), "the match is at the start, so nothing is elided there")
    }
}
