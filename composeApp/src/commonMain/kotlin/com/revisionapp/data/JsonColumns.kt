package com.revisionapp.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Wire format for the columns that hold structured data as JSON text.
 *
 * Kept separate from the content-pack DTOs on purpose: the on-disk format must
 * not change just because the `content` branch format evolves.
 */
@Serializable
data class KeyPointJson(
    val text: String,
    val synonyms: List<String> = emptyList(),
    val mustInclude: Boolean = false,
    val weight: Double = 1.0,
)

@Serializable
data class McqJson(
    val correct: String,
    val distractors: List<String> = emptyList(),
)

@Serializable
data class NumericJson(
    val value: Double? = null,
    val tolerance: Double? = null,
    val unit: String? = null,
)

internal val KeyPointListSerializer = ListSerializer(KeyPointJson.serializer())
internal val StringListSerializer = ListSerializer(String.serializer())
