package com.revisionapp.data.content

import kotlinx.serialization.Serializable

/**
 * The wire format of the `content` branch. These DTOs mirror the JSON Schema
 * files in that branch's `schema` directory one for one; [ContentMapper] turns
 * them into domain models, so the domain never sees a serialisation annotation.
 */

@Serializable
enum class AnswerTypeDto {
    TEXT,
    NUMERIC,
    EXPRESSION,
    SELF_GRADE,
}

@Serializable
enum class ReviewStatusDto {
    AI_UNREVIEWED,
    REVIEWED,
}

@Serializable
data class ContentManifestDto(
    val schemaVersion: Int,
    val packs: List<PackSummaryDto> = emptyList(),
    val generatedAt: String? = null,
)

@Serializable
data class PackSummaryDto(
    val id: String,
    val name: String,
    val version: Int,
    val path: String,
    val sha256: String,
    val cardCount: Int,
)

@Serializable
data class PackDto(
    val packId: String,
    val name: String,
    val version: Int,
    val topics: List<TopicDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    /**
     * `raw.githubusercontent.com` serves single files and has no directory
     * listing, so a pack has to declare the card files it owns.
     */
    val cardFiles: List<String> = emptyList(),
    val description: String? = null,
    val structureVerified: Boolean = true,
)

@Serializable
data class TopicDto(
    val slug: String,
    val name: String,
    val parent: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class TagDto(
    val slug: String,
    val group: String,
    val name: String,
)

@Serializable
data class CardFileDto(
    /** Slug of the topic every card in this file belongs to. */
    val topic: String,
    val cards: List<CardDto> = emptyList(),
    /** Tag slugs, resolved against `pack.json`, applied to every card here. */
    val tagRefs: List<String> = emptyList(),
)

@Serializable
data class CardDto(
    /** Four-digit ordinal within the topic, e.g. `0012`. */
    val id: String,
    val front: String,
    val back: String,
    val answerType: AnswerTypeDto = AnswerTypeDto.TEXT,
    val keyPoints: List<KeyPointDto> = emptyList(),
    val acceptedAliases: List<String> = emptyList(),
    val tileAnswer: List<String>? = null,
    val mcq: McqDto? = null,
    val explanation: String? = null,
    val numeric: NumericDto? = null,
    val reviewStatus: ReviewStatusDto = ReviewStatusDto.AI_UNREVIEWED,
    val specRef: String = "",
    /** Extra tag slugs for this card only, on top of the file's `tagRefs`. */
    val tagRefs: List<String> = emptyList(),
)

@Serializable
data class KeyPointDto(
    val text: String,
    val synonyms: List<String> = emptyList(),
    val mustInclude: Boolean = false,
    val weight: Double = 1.0,
)

@Serializable
data class McqDto(
    val correct: String,
    val distractors: List<String> = emptyList(),
)

@Serializable
data class NumericDto(
    val value: Double? = null,
    val tolerance: Double? = null,
    val unit: String? = null,
)
