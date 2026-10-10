package com.revisionapp.domain.model

/** Where a piece of content came from. Drives whether it may be edited. */
enum class ContentSource {
    /** Downloaded from the `content` branch. Read-only, but duplicatable. */
    BUILTIN,

    /** Created in the app. Editable and never touched by a content sync. */
    USER,
    ;

    val isEditable: Boolean get() = this == USER
}

/** How an answer is checked. */
enum class AnswerType {
    /** Free text, judged by the [com.revisionapp.domain.check.AnswerChecker]. */
    TEXT,

    /** A number, compared with a tolerance. */
    NUMERIC,

    /** An algebraic expression, compared structurally. */
    EXPRESSION,

    /** No automatic check: the model answer is shown and the user judges. */
    SELF_GRADE,
}

/** Whether a card's content has been checked by a human. */
enum class ReviewStatus {
    AI_UNREVIEWED,
    REVIEWED,
}

/**
 * One thing the answer has to contain. [mustInclude] points are mandatory for a
 * `CORRECT` verdict; [synonyms] are alternative phrasings that also count.
 */
data class KeyPoint(
    val text: String,
    val synonyms: List<String> = emptyList(),
    val mustInclude: Boolean = false,
    val weight: Double = 1.0,
)

/**
 * Optional numeric metadata for [AnswerType.NUMERIC] cards. When [value] is null
 * the expected number is parsed out of [Card.back], so authors usually only have
 * to supply a [tolerance]. See docs/DECISIONS.md D16.
 */
data class NumericSpec(
    val value: Double? = null,
    /** Relative tolerance as a fraction: `0.05` means +/- 5%. Defaults to 2%. */
    val tolerance: Double? = null,
    /** Display only; units are stripped before comparison. */
    val unit: String? = null,
) {
    companion object {
        const val DEFAULT_TOLERANCE: Double = 0.02
    }
}

/** An authored multiple-choice option set: one correct answer plus distractors. */
data class Mcq(
    val correct: String,
    val distractors: List<String>,
) {
    /** Every option, unshuffled, correct answer first. */
    val options: List<String> get() = listOf(correct) + distractors

    /** True when there are exactly three distinct, non-blank distractors. */
    val isUsable: Boolean
        get() = correct.isNotBlank() &&
            distractors.size >= MIN_DISTRACTORS &&
            distractors.none { it.isBlank() } &&
            options.map { it.trim().lowercase() }.toSet().size == options.size

    companion object {
        const val MIN_DISTRACTORS: Int = 3
        const val OPTION_COUNT: Int = 4
    }
}

/**
 * The single source of truth for content. Every question in every study mode is
 * *derived* from a card, so one card can power flashcards, typed answers, word
 * tiles and multiple choice.
 */
data class Card(
    val id: CardId,
    val topicId: TopicId,
    val tagIds: Set<TagId> = emptySet(),
    val front: String,
    val back: String,
    val answerType: AnswerType = AnswerType.TEXT,
    val keyPoints: List<KeyPoint> = emptyList(),
    val acceptedAliases: List<String> = emptyList(),
    /** Legacy authored tile chunks; preserved when older packs are imported or edited. */
    val tileAnswer: List<String>? = null,
    val mcq: Mcq? = null,
    val explanation: String? = null,
    val numeric: NumericSpec? = null,
    val source: ContentSource = ContentSource.USER,
    val reviewStatus: ReviewStatus = ReviewStatus.AI_UNREVIEWED,
    /** Free-text pointer at the specification section this card covers. */
    val specRef: String = "",
    /** Non-null for built-in content; the pack it was downloaded from. */
    val packId: PackId? = null,
) {
    val isEditable: Boolean get() = source.isEditable

    /** Must-include key points, used by the answer checker and the UI. */
    val mandatoryKeyPoints: List<KeyPoint> get() = keyPoints.filter { it.mustInclude }
}

/** A node in an arbitrarily deep topic tree. */
data class Topic(
    val id: TopicId,
    val name: String,
    val parentId: TopicId? = null,
    val sortOrder: Int = 0,
    val source: ContentSource = ContentSource.USER,
    val packId: PackId? = null,
) {
    val isEditable: Boolean get() = source.isEditable
}

/** A user-visible label. [group] controls how filters combine. */
data class Tag(
    val id: TagId,
    val name: String,
    val group: TagGroup = TagGroup.Custom,
    val source: ContentSource = ContentSource.USER,
) {
    val isEditable: Boolean get() = source.isEditable
}

/** A pack as recorded once it has been imported. */
data class InstalledPack(
    val id: PackId,
    val name: String,
    val version: Int,
    val sha256: String,
    val cardCount: Int,
)
