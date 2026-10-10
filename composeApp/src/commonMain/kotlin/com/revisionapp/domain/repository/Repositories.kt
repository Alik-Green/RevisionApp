package com.revisionapp.domain.repository

import com.revisionapp.domain.check.TermCorpus
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.InstalledPack
import com.revisionapp.domain.model.PackId
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.Topic
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.srs.Rating
import com.revisionapp.domain.srs.ScheduleState
import kotlin.time.Instant

/** One completed review, as recorded for stats and streaks. */
data class ReviewEntry(
    val cardId: CardId,
    val mode: StudyMode,
    val rating: Rating,
    val verdict: VerdictKind?,
    val correct: Boolean,
    val reviewedAt: Instant,
)

/** Accuracy for one topic, aggregated over every mode. */
data class TopicAccuracy(
    val topicId: TopicId,
    val reviews: Int,
    val correct: Int,
) {
    val accuracy: Double get() = if (reviews <= 0) 0.0 else correct.toDouble() / reviews
}

/**
 * Reading and writing the user's library. Implementations must keep built-in and
 * user content strictly apart: nothing here may rewrite a `BUILTIN` row except a
 * content sync, and nothing may touch study progress.
 */
interface LibraryRepository {
    fun topics(): List<Topic>
    fun tags(): List<Tag>
    fun cards(): List<Card>
    fun card(id: CardId): Card?

    /** Cards in the same topic, used for MCQ distractors and tile decoys. */
    fun siblingsOf(card: Card): List<Card>

    fun saveTopic(topic: Topic)
    fun deleteTopic(id: TopicId)
    fun saveTag(tag: Tag)
    fun deleteTag(id: TagId)
    fun saveCard(card: Card)
    fun deleteCard(id: CardId)

    /**
     * Copies a card — built-in or not — into user content under [newId]. The
     * original is never modified, which is how read-only built-in content becomes
     * editable.
     */
    fun duplicateAsUser(card: Card, newId: CardId, topicId: TopicId?): Card

    /** Every model answer, for the TF-IDF fallback's inverse document frequencies. */
    fun corpus(): TermCorpus
}

/** Study progress. Written by every mode, read by the browser and the stats. */
interface ProgressRepository {
    fun states(): Map<CardId, ScheduleState>
    fun saveState(cardId: CardId, state: ScheduleState)
    fun deleteState(cardId: CardId)
    fun record(entry: ReviewEntry)

    /** Distinct local days with at least one review, most recent first. */
    fun reviewDays(): List<String>

    fun accuracyByTopic(): Map<TopicId, TopicAccuracy>
    fun reviewCount(): Int
}

/**
 * Answers the user has insisted were correct, learned per card.
 *
 * Kept out of `card` on purpose: built-in cards are read-only and a pack re-import
 * replaces their rows, so anything learned has to live beside the card, keyed by
 * its id, to survive a content update.
 */
interface LearnedAnswerStore {

    /** Every learned answer, keyed by card. Read once when a session is built. */
    fun all(): Map<CardId, List<String>>

    /** Records [answer] for [cardId], ignoring blanks and wordings already known. */
    fun add(cardId: CardId, answer: String)

    fun remove(cardId: CardId)
}

/** Installed content packs, and the atomic import that maintains them. */
interface PackStore {
    fun installed(): List<InstalledPack>
    fun find(id: PackId): InstalledPack?

    /**
     * Replaces everything this pack owns, in one transaction. Only rows tagged
     * with the pack id are removed; user content and `card_state` are untouched,
     * so a re-import cannot disturb study progress.
     */
    fun import(pack: InstalledPack, topics: List<Topic>, tags: List<Tag>, cards: List<Card>)
}

/** Small key/value store for user settings (content sources, retention, ...). */
interface SettingsStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun all(): Map<String, String>
}
