package com.revisionapp.data.repository

import com.revisionapp.data.AppJson
import com.revisionapp.data.KeyPointJson
import com.revisionapp.data.KeyPointListSerializer
import com.revisionapp.data.McqJson
import com.revisionapp.data.StringListSerializer
import com.revisionapp.data.db.RevisionDatabase
import com.revisionapp.domain.check.InMemoryTermCorpus
import com.revisionapp.domain.check.TermCorpus
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
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.TagGroup
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.Topic
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.repository.LibraryRepository
import com.revisionapp.domain.repository.PackStore
import com.revisionapp.domain.repository.ProgressRepository
import com.revisionapp.domain.repository.ReviewEntry
import com.revisionapp.domain.repository.SettingsStore
import com.revisionapp.domain.repository.TopicAccuracy
import com.revisionapp.domain.srs.LearningState
import com.revisionapp.domain.srs.ScheduleState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

// Every enum is stored as its name and parsed back defensively, so a value
// written by a newer version of the app degrades instead of crashing.
internal fun parseAnswerType(raw: String): AnswerType =
    AnswerType.entries.firstOrNull { it.name == raw } ?: AnswerType.TEXT

internal fun parseContentSource(raw: String): ContentSource =
    ContentSource.entries.firstOrNull { it.name == raw } ?: ContentSource.USER

internal fun parseReviewStatus(raw: String): ReviewStatus =
    ReviewStatus.entries.firstOrNull { it.name == raw } ?: ReviewStatus.AI_UNREVIEWED

internal fun parseLearningState(raw: String): LearningState =
    LearningState.entries.firstOrNull { it.name == raw } ?: LearningState.NEW

/**
 * Writes a card row and its tag links. Shared by the library repository and the
 * pack importer so that both produce identical rows.
 *
 * Arguments are positional and follow the column order in `Card.sq` exactly.
 */
internal object CardWriter {

    fun write(database: RevisionDatabase, json: Json, card: Card, createdAt: Long, updatedAt: Long) {
        database.cardQueries.upsert(
            card.id.value,
            card.topicId.value,
            card.front,
            card.back,
            card.answerType.name,
            json.encodeToString(KeyPointListSerializer, card.keyPoints.map { it.toWire() }),
            json.encodeToString(StringListSerializer, card.acceptedAliases),
            card.tileAnswer?.let { json.encodeToString(StringListSerializer, it) },
            card.mcq?.let { json.encodeToString(McqJson.serializer(), McqJson(it.correct, it.distractors)) },
            card.explanation,
            card.numeric?.value,
            card.numeric?.tolerance,
            card.numeric?.unit,
            card.source.name,
            card.reviewStatus.name,
            card.specRef,
            card.packId?.value,
            createdAt,
            updatedAt,
        )
        database.cardTagQueries.deleteByCardId(card.id.value)
        for (tagId in card.tagIds) {
            database.cardTagQueries.insert(card.id.value, tagId.value)
        }
    }

    private fun KeyPoint.toWire(): KeyPointJson = KeyPointJson(text, synonyms, mustInclude, weight)
}

class SqlLibraryRepository(
    private val database: RevisionDatabase,
    private val json: Json = AppJson.instance,
    private val clock: Clock = Clock.System,
) : LibraryRepository {

    override fun topics(): List<Topic> = database.topicQueries.selectAll().executeAsList().map { row ->
        Topic(
            id = TopicId(row.id),
            name = row.name,
            parentId = row.parentId?.let { TopicId(it) },
            sortOrder = row.sortOrder.toInt(),
            source = parseContentSource(row.source),
            packId = row.packId?.let { PackId(it) },
        )
    }

    override fun tags(): List<Tag> = database.tagQueries.selectAll().executeAsList().map { row ->
        Tag(
            id = TagId(row.id),
            name = row.name,
            group = TagGroup(row.tagGroup),
            source = parseContentSource(row.source),
        )
    }

    override fun cards(): List<Card> {
        val tagsByCard = database.cardTagQueries.selectAll().executeAsList()
            .groupBy({ it.cardId }, { TagId(it.tagId) })

        return database.cardQueries.selectAll().executeAsList().map { row ->
            Card(
                id = CardId(row.id),
                topicId = TopicId(row.topicId),
                tagIds = (tagsByCard[row.id] ?: emptyList()).toSet(),
                front = row.front,
                back = row.back,
                answerType = parseAnswerType(row.answerType),
                keyPoints = decodeKeyPoints(row.keyPoints),
                acceptedAliases = decodeStrings(row.acceptedAliases),
                tileAnswer = row.tileAnswer?.let { decodeStrings(it) },
                mcq = row.mcq?.let { decodeMcq(it) },
                explanation = row.explanation,
                numeric = decodeNumeric(row.numericValue, row.numericTolerance, row.numericUnit),
                source = parseContentSource(row.source),
                reviewStatus = parseReviewStatus(row.reviewStatus),
                specRef = row.specRef,
                packId = row.packId?.let { PackId(it) },
            )
        }
    }

    override fun card(id: CardId): Card? = cards().firstOrNull { it.id == id }

    override fun siblingsOf(card: Card): List<Card> =
        cards().filter { it.topicId == card.topicId && it.id != card.id }

    override fun saveTopic(topic: Topic) {
        database.topicQueries.upsert(
            topic.id.value,
            topic.name,
            topic.parentId?.value,
            topic.sortOrder.toLong(),
            topic.source.name,
            topic.packId?.value,
        )
    }

    override fun deleteTopic(id: TopicId) {
        database.topicQueries.deleteById(id.value)
    }

    override fun saveTag(tag: Tag) {
        database.tagQueries.upsert(tag.id.value, tag.name, tag.group.value, tag.source.name)
    }

    override fun deleteTag(id: TagId) {
        database.tagQueries.deleteById(id.value)
    }

    override fun saveCard(card: Card) {
        val now = clock.now().toEpochMilliseconds()
        val createdAt = database.cardQueries.selectAll().executeAsList()
            .firstOrNull { it.id == card.id.value }
            ?.createdAt
            ?: now
        CardWriter.write(database, json, card, createdAt, now)
    }

    override fun deleteCard(id: CardId) {
        database.cardTagQueries.deleteByCardId(id.value)
        database.cardQueries.deleteById(id.value)
        // Progress is deliberately kept: re-creating a card with the same id
        // restores its schedule rather than starting over.
    }

    override fun duplicateAsUser(card: Card, newId: CardId, topicId: TopicId?): Card {
        val copy = card.copy(
            id = newId,
            topicId = topicId ?: card.topicId,
            source = ContentSource.USER,
            packId = null,
            reviewStatus = ReviewStatus.AI_UNREVIEWED,
        )
        saveCard(copy)
        return copy
    }

    override fun corpus(): TermCorpus = InMemoryTermCorpus(cards().map { it.back })

    private fun decodeKeyPoints(raw: String): List<KeyPoint> = runCatching {
        json.decodeFromString(KeyPointListSerializer, raw).map { KeyPoint(it.text, it.synonyms, it.mustInclude, it.weight) }
    }.getOrDefault(emptyList())

    private fun decodeStrings(raw: String): List<String> =
        runCatching { json.decodeFromString(StringListSerializer, raw) }.getOrDefault(emptyList())

    private fun decodeMcq(raw: String): Mcq? = runCatching {
        json.decodeFromString(McqJson.serializer(), raw).let { Mcq(it.correct, it.distractors) }
    }.getOrNull()

    private fun decodeNumeric(value: Double?, tolerance: Double?, unit: String?): NumericSpec? =
        if (value == null && tolerance == null && unit == null) null else NumericSpec(value, tolerance, unit)
}

class SqlProgressRepository(
    private val database: RevisionDatabase,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ProgressRepository {

    override fun states(): Map<CardId, ScheduleState> =
        database.cardStateQueries.selectAll().executeAsList().associate { row ->
            CardId(row.cardId) to ScheduleState(
                stability = row.stability,
                difficulty = row.difficulty,
                dueAt = Instant.fromEpochMilliseconds(row.dueAt),
                lastReviewAt = row.lastReviewAt?.let { Instant.fromEpochMilliseconds(it) },
                reps = row.reps.toInt(),
                lapses = row.lapses.toInt(),
                state = parseLearningState(row.state),
                modeEscalation = row.modeEscalation.toInt(),
            )
        }

    override fun saveState(cardId: CardId, state: ScheduleState) {
        database.cardStateQueries.upsert(
            cardId.value,
            state.stability,
            state.difficulty,
            state.dueAt.toEpochMilliseconds(),
            state.lastReviewAt?.toEpochMilliseconds(),
            state.reps.toLong(),
            state.lapses.toLong(),
            state.state.name,
            state.modeEscalation.toLong(),
        )
    }

    override fun deleteState(cardId: CardId) {
        database.cardStateQueries.deleteById(cardId.value)
    }

    override fun record(entry: ReviewEntry) {
        database.reviewLogQueries.insert(
            entry.cardId.value,
            entry.mode.name,
            entry.rating.name,
            entry.verdict?.name,
            if (entry.correct) 1L else 0L,
            entry.reviewedAt.toEpochMilliseconds(),
            entry.reviewedAt.toLocalDateTime(timeZone).date.toString(),
        )
    }

    override fun reviewDays(): List<String> = database.reviewLogQueries.selectDistinctDays().executeAsList()

    override fun accuracyByTopic(): Map<TopicId, TopicAccuracy> =
        database.reviewLogQueries.accuracyByTopic().executeAsList().associate { row ->
            val topicId = TopicId(row.topicId)
            topicId to TopicAccuracy(
                topicId = topicId,
                reviews = row.total.toInt(),
                correct = (row.correctCount ?: 0L).toInt(),
            )
        }

    override fun reviewCount(): Int = database.reviewLogQueries.countAll().executeAsOne().toInt()
}

class SqlPackStore(
    private val database: RevisionDatabase,
    private val json: Json = AppJson.instance,
    private val clock: Clock = Clock.System,
) : PackStore {

    override fun installed(): List<InstalledPack> =
        database.packQueries.selectAll().executeAsList().map { row ->
            InstalledPack(
                id = PackId(row.id),
                name = row.name,
                version = row.version.toInt(),
                sha256 = row.sha256,
                cardCount = row.cardCount.toInt(),
            )
        }

    override fun find(id: PackId): InstalledPack? = installed().firstOrNull { it.id == id }

    override fun import(pack: InstalledPack, topics: List<Topic>, tags: List<Tag>, cards: List<Card>) {
        val now = clock.now().toEpochMilliseconds()

        // One transaction: if anything throws, the previous version of this pack
        // is still on disk and study progress was never touched.
        database.transaction {
            val previousCardIds = database.cardQueries.selectIdsByPack(pack.id.value).executeAsList()
            if (previousCardIds.isNotEmpty()) {
                database.cardTagQueries.deleteByCardIds(previousCardIds)
            }
            database.cardQueries.deleteByPack(pack.id.value)
            database.topicQueries.deleteByPack(pack.id.value)

            for (tag in tags) {
                database.tagQueries.upsert(tag.id.value, tag.name, tag.group.value, tag.source.name)
            }
            for (topic in topics) {
                database.topicQueries.upsert(
                    topic.id.value,
                    topic.name,
                    topic.parentId?.value,
                    topic.sortOrder.toLong(),
                    topic.source.name,
                    topic.packId?.value,
                )
            }
            for (card in cards) {
                CardWriter.write(database, json, card, now, now)
            }
            database.packQueries.upsert(
                pack.id.value,
                pack.name,
                pack.version.toLong(),
                pack.sha256,
                pack.cardCount.toLong(),
                now,
            )
        }
    }
}

class SqlSettingsStore(private val database: RevisionDatabase) : SettingsStore {

    override fun read(key: String): String? = all()[key]

    override fun write(key: String, value: String) {
        database.settingQueries.upsert(key, value)
    }

    override fun all(): Map<String, String> =
        database.settingQueries.selectAll().executeAsList().associate { it.settingKey to it.settingValue }
}
