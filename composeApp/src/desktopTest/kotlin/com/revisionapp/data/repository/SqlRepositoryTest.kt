package com.revisionapp.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.revisionapp.data.db.RevisionDatabase
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.AnswerType
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.ContentSource
import com.revisionapp.domain.model.InstalledPack
import com.revisionapp.domain.model.KeyPoint
import com.revisionapp.domain.model.Mcq
import com.revisionapp.domain.model.NumericSpec
import com.revisionapp.domain.model.PackId
import com.revisionapp.domain.model.ReviewStatus
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.TagGroup
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.Topic
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.repository.ReviewEntry
import com.revisionapp.domain.srs.LearningState
import com.revisionapp.domain.srs.Rating
import com.revisionapp.domain.srs.ScheduleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Integration tests against a real (in-memory) SQLite database, so the SQLDelight
 * schema, the generated queries and the row mapping are all exercised rather than
 * assumed.
 */
class SqlRepositoryTest {

    private val now: Instant = Instant.fromEpochMilliseconds(1_800_000_000_000L)
    private val packId = PackId("sample")
    private val builtInTopicId = TopicId("builtin:sample:circular-motion")
    private val builtInTagId = TagId("builtin:tag:board:ocr")
    private val builtInCardId = CardId("builtin:sample:circular-motion:0001")

    private fun newDatabase(): RevisionDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RevisionDatabase.Schema.create(driver)
        return RevisionDatabase(driver)
    }

    private fun builtInCard(front: String = "front", back: String = "back"): Card = Card(
        id = builtInCardId,
        topicId = builtInTopicId,
        tagIds = setOf(builtInTagId),
        front = front,
        back = back,
        answerType = AnswerType.TEXT,
        keyPoints = listOf(KeyPoint("centripetal", synonyms = listOf("centre-seeking"), mustInclude = true)),
        acceptedAliases = listOf("an alias"),
        explanation = "because",
        source = ContentSource.BUILTIN,
        reviewStatus = ReviewStatus.AI_UNREVIEWED,
        specRef = "5.1.2 (a)",
        packId = packId,
    )

    private fun userCard(id: String = "user:card-1", tags: Set<TagId> = setOf(TagId("user:tag-1"))): Card = Card(
        id = CardId(id),
        topicId = builtInTopicId,
        tagIds = tags,
        front = "my question",
        back = "my answer",
        answerType = AnswerType.NUMERIC,
        numeric = NumericSpec(value = 9.81, tolerance = 0.01, unit = "m/s^2"),
        mcq = Mcq("my answer", listOf("one", "two", "three")),
        tileAnswer = listOf("my", "answer"),
        source = ContentSource.USER,
    )

    private fun sampleTopics(): List<Topic> = listOf(
        Topic(TopicId("builtin:sample:physics"), "Physics", null, 0, ContentSource.BUILTIN, packId),
        Topic(builtInTopicId, "Circular motion", TopicId("builtin:sample:physics"), 1, ContentSource.BUILTIN, packId),
    )

    private fun sampleTags(): List<Tag> = listOf(
        Tag(builtInTagId, "OCR", TagGroup("board"), ContentSource.BUILTIN),
    )

    @Test
    fun aCardSurvivesAWriteReadRoundTripWithEveryField() {
        val database = newDatabase()
        val library = SqlLibraryRepository(database)

        val original = builtInCard().copy(
            keyPoints = listOf(
                KeyPoint("centripetal", synonyms = listOf("centre-seeking"), mustInclude = true, weight = 2.0),
                KeyPoint("towards the centre"),
            ),
        )
        library.saveCard(original)

        assertEquals(original, library.card(builtInCardId))
        assertEquals(listOf(original), library.cards())
    }

    @Test
    fun aUserCardWithNumericAndMcqDataSurvivesTheRoundTrip() {
        val database = newDatabase()
        val library = SqlLibraryRepository(database)
        val original = userCard()

        library.saveCard(original)

        assertEquals(original, library.card(CardId("user:card-1")))
    }

    @Test
    fun topicsAndTagsRoundTrip() {
        val database = newDatabase()
        val library = SqlLibraryRepository(database)

        sampleTopics().forEach { library.saveTopic(it) }
        sampleTags().forEach { library.saveTag(it) }

        assertEquals(sampleTopics().map { it.id }.toSet(), library.topics().map { it.id }.toSet())
        assertEquals("Circular motion", library.topics().first { it.id == builtInTopicId }.name)
        assertEquals(builtInTopicId, library.topics().first { it.id == builtInTopicId }.parentId)
        assertEquals(sampleTags(), library.tags())
    }

    @Test
    fun reimportingAPackUpdatesContentButKeepsUserCardsAndProgress() {
        val database = newDatabase()
        val library = SqlLibraryRepository(database)
        val progress = SqlProgressRepository(database)
        val packs = SqlPackStore(database)

        packs.import(
            InstalledPack(packId, "Sample", 1, "hash-v1", 1),
            sampleTopics(),
            sampleTags(),
            listOf(builtInCard(front = "version one")),
        )

        // The user adds their own content and studies the built-in card.
        library.saveTopic(Topic(TopicId("user:topic"), "Mine", builtInTopicId, 5, ContentSource.USER, null))
        library.saveCard(userCard())
        val schedule = ScheduleState(
            stability = 12.5,
            difficulty = 4.0,
            dueAt = now,
            lastReviewAt = now,
            reps = 3,
            lapses = 1,
            state = LearningState.REVIEW,
            modeEscalation = 2,
        )
        progress.saveState(builtInCardId, schedule)
        progress.record(
            ReviewEntry(builtInCardId, StudyMode.TYPED, Rating.GOOD, VerdictKind.CORRECT, true, now),
        )

        // The pack ships a new version of the same card id.
        packs.import(
            InstalledPack(packId, "Sample", 2, "hash-v2", 1),
            sampleTopics(),
            sampleTags(),
            listOf(builtInCard(front = "version two")),
        )

        val cards = library.cards()
        assertEquals(2, cards.size, "the user card must survive a content update")
        assertEquals("version two", cards.first { it.id == builtInCardId }.front)
        assertEquals(setOf(builtInTagId), cards.first { it.id == builtInCardId }.tagIds)

        val survivor = cards.first { it.id == CardId("user:card-1") }
        assertEquals(userCard(), survivor)
        assertEquals(setOf(TagId("user:tag-1")), survivor.tagIds)

        assertTrue(library.topics().any { it.id == TopicId("user:topic") })
        assertEquals(schedule, progress.states()[builtInCardId])
        assertEquals(1, progress.reviewCount())
        assertEquals(2, packs.installed().single().version)
        assertEquals("hash-v2", packs.find(packId)?.sha256)
    }

    @Test
    fun anImportOnlyRemovesWhatTheSamePackOwns() {
        val database = newDatabase()
        val packs = SqlPackStore(database)
        val otherPackId = PackId("other")
        val otherCardId = CardId("builtin:other:topic:0001")

        packs.import(
            InstalledPack(packId, "Sample", 1, "a", 1),
            sampleTopics(),
            sampleTags(),
            listOf(builtInCard()),
        )
        packs.import(
            InstalledPack(otherPackId, "Other", 1, "b", 1),
            listOf(Topic(TopicId("builtin:other:topic"), "Other topic", null, 0, ContentSource.BUILTIN, otherPackId)),
            emptyList(),
            listOf(builtInCard().copy(id = otherCardId, topicId = TopicId("builtin:other:topic"), packId = otherPackId)),
        )

        packs.import(
            InstalledPack(packId, "Sample", 2, "c", 1),
            sampleTopics(),
            sampleTags(),
            listOf(builtInCard(front = "refreshed")),
        )

        val ids = SqlLibraryRepository(database).cards().map { it.id }.toSet()
        assertEquals(setOf(builtInCardId, otherCardId), ids)
        assertEquals(2, packs.installed().size)
    }

    @Test
    fun duplicatingABuiltInCardProducesAnEditableUserCopy() {
        val database = newDatabase()
        val library = SqlLibraryRepository(database)
        val packs = SqlPackStore(database)
        packs.import(InstalledPack(packId, "Sample", 1, "a", 1), sampleTopics(), sampleTags(), listOf(builtInCard()))

        val copy = library.duplicateAsUser(
            checkNotNull(library.card(builtInCardId)),
            CardId("user:copy"),
            topicId = TopicId("user:topic"),
        )

        assertEquals(ContentSource.USER, copy.source)
        assertNull(copy.packId)
        assertEquals(TopicId("user:topic"), copy.topicId)
        assertTrue(copy.isEditable)
        assertEquals(2, library.cards().size)
        // The original is untouched.
        assertEquals(ContentSource.BUILTIN, library.card(builtInCardId)?.source)
    }

    @Test
    fun deletingACardRemovesItsTagLinksButKeepsProgress() {
        val database = newDatabase()
        val library = SqlLibraryRepository(database)
        val progress = SqlProgressRepository(database)

        library.saveCard(userCard())
        progress.saveState(CardId("user:card-1"), ScheduleState.new(now))

        library.deleteCard(CardId("user:card-1"))

        assertNull(library.card(CardId("user:card-1")))
        assertNotNull(progress.states()[CardId("user:card-1")])
    }

    @Test
    fun siblingsComeFromTheSameTopicOnly() {
        val database = newDatabase()
        val library = SqlLibraryRepository(database)
        library.saveCard(userCard(id = "user:a"))
        library.saveCard(userCard(id = "user:b"))
        library.saveCard(
            userCard(id = "user:c").copy(topicId = TopicId("user:elsewhere")),
        )

        val siblings = library.siblingsOf(checkNotNull(library.card(CardId("user:a"))))

        assertEquals(setOf(CardId("user:b")), siblings.map { it.id }.toSet())
    }

    @Test
    fun theCorpusCoversEveryModelAnswer() {
        val database = newDatabase()
        val library = SqlLibraryRepository(database)
        library.saveCard(userCard(id = "user:a"))
        library.saveCard(userCard(id = "user:b").copy(back = "centripetal force"))

        val corpus = library.corpus()

        assertEquals(2, corpus.documentCount)
        assertTrue(corpus.documentFrequency("centripetal") >= 1)
    }

    @Test
    fun scheduleStateRoundTripsExactly() {
        val database = newDatabase()
        val progress = SqlProgressRepository(database)
        val state = ScheduleState(
            stability = 3.7145,
            difficulty = 5.1618,
            dueAt = now,
            lastReviewAt = now,
            reps = 7,
            lapses = 2,
            state = LearningState.RELEARNING,
            modeEscalation = 3,
        )

        progress.saveState(builtInCardId, state)

        assertEquals(state, progress.states()[builtInCardId])
        progress.deleteState(builtInCardId)
        assertTrue(progress.states().isEmpty())
    }

    @Test
    fun theReviewLogDrivesStreaksAndAccuracy() {
        val database = newDatabase()
        val library = SqlLibraryRepository(database)
        val progress = SqlProgressRepository(database)
        library.saveCard(builtInCard())

        progress.record(ReviewEntry(builtInCardId, StudyMode.MCQ, Rating.GOOD, VerdictKind.CORRECT, true, now))
        progress.record(ReviewEntry(builtInCardId, StudyMode.MCQ, Rating.AGAIN, VerdictKind.INCORRECT, false, now))

        assertEquals(2, progress.reviewCount())
        assertEquals(1, progress.reviewDays().size)

        val accuracy = progress.accuracyByTopic()[builtInTopicId]
        assertNotNull(accuracy)
        assertEquals(2, accuracy.reviews)
        assertEquals(1, accuracy.correct)
        assertEquals(0.5, accuracy.accuracy, 1e-9)
    }

    @Test
    fun settingsRoundTrip() {
        val database = newDatabase()
        val settings = SqlSettingsStore(database)

        assertNull(settings.read("baseUrl"))
        settings.write("baseUrl", "https://example.test/content")
        assertEquals("https://example.test/content", settings.read("baseUrl"))

        settings.write("baseUrl", "https://other.test/content")
        assertEquals(1, settings.all().size)
        assertEquals("https://other.test/content", settings.read("baseUrl"))
    }
}
