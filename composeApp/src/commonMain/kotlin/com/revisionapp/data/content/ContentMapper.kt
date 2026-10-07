package com.revisionapp.data.content

import com.revisionapp.domain.model.AnswerType
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.ContentSource
import com.revisionapp.domain.model.Ids
import com.revisionapp.domain.model.KeyPoint
import com.revisionapp.domain.model.Mcq
import com.revisionapp.domain.model.NumericSpec
import com.revisionapp.domain.model.PackId
import com.revisionapp.domain.model.ReviewStatus
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.TagGroup
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.Topic

/**
 * Turns content-branch DTOs into domain models, stamping every id with the
 * `builtin:` namespace so that a sync can never collide with user content.
 */
object ContentMapper {

    fun topics(packId: PackId, pack: PackDto): List<Topic> = pack.topics.map { dto ->
        Topic(
            id = Ids.builtInTopic(packId, dto.slug),
            name = dto.name,
            parentId = dto.parent?.let { Ids.builtInTopic(packId, it) },
            sortOrder = dto.sortOrder,
            source = ContentSource.BUILTIN,
            packId = packId,
        )
    }

    /**
     * Tag ids are global rather than per-pack, because several packs legitimately
     * share a tag such as `board = OCR`. See docs/DECISIONS.md D13.
     */
    fun tags(pack: PackDto): List<Tag> = pack.tags.map { dto ->
        Tag(
            id = Ids.builtInTag(TagGroup(dto.group), dto.slug),
            name = dto.name,
            group = TagGroup(dto.group),
            source = ContentSource.BUILTIN,
        )
    }

    fun cards(packId: PackId, pack: PackDto, file: CardFileDto): List<Card> {
        val groupBySlug = pack.tags.associate { it.slug to it.group }
        val fileTags = file.tagRefs.mapNotNull { resolveTag(it, groupBySlug) }

        return file.cards.mapIndexed { index, dto ->
            val ordinal = dto.id.trim().toIntOrNull() ?: (index + 1)
            val cardTags = (fileTags + dto.tagRefs.mapNotNull { resolveTag(it, groupBySlug) }).toSet()
            Card(
                id = Ids.builtInCard(packId, file.topic, ordinal),
                topicId = Ids.builtInTopic(packId, file.topic),
                tagIds = cardTags,
                front = dto.front,
                back = dto.back,
                answerType = answerType(dto.answerType),
                keyPoints = dto.keyPoints.map { KeyPoint(it.text, it.synonyms, it.mustInclude, it.weight) },
                acceptedAliases = dto.acceptedAliases,
                tileAnswer = dto.tileAnswer,
                mcq = dto.mcq?.let { Mcq(it.correct, it.distractors) },
                explanation = dto.explanation,
                numeric = dto.numeric?.let { NumericSpec(it.value, it.tolerance, it.unit) },
                source = ContentSource.BUILTIN,
                reviewStatus = reviewStatus(dto.reviewStatus),
                specRef = dto.specRef,
                packId = packId,
            )
        }
    }

    fun answerType(dto: AnswerTypeDto): AnswerType = when (dto) {
        AnswerTypeDto.TEXT -> AnswerType.TEXT
        AnswerTypeDto.NUMERIC -> AnswerType.NUMERIC
        AnswerTypeDto.EXPRESSION -> AnswerType.EXPRESSION
        AnswerTypeDto.SELF_GRADE -> AnswerType.SELF_GRADE
    }

    fun reviewStatus(dto: ReviewStatusDto): ReviewStatus = when (dto) {
        ReviewStatusDto.AI_UNREVIEWED -> ReviewStatus.AI_UNREVIEWED
        ReviewStatusDto.REVIEWED -> ReviewStatus.REVIEWED
    }

    private fun resolveTag(slug: String, groupBySlug: Map<String, String>): TagId? =
        groupBySlug[slug]?.let { Ids.builtInTag(TagGroup(it), slug) }
}
