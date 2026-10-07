package com.revisionapp.domain.model

import kotlin.random.Random

/**
 * Identifiers are value classes rather than `String` typealiases so that a
 * [TopicId] can never be handed to something that wants a [CardId]. The wire and
 * database representation is always the wrapped [value].
 */
@JvmInline
value class PackId(val value: String)

@JvmInline
value class TopicId(val value: String)

@JvmInline
value class TagId(val value: String)

@JvmInline
value class CardId(val value: String)

/**
 * An open-ended tag grouping (`board`, `level`, `subject`, `paper`,
 * `difficulty`, ...). A value class rather than an enum because users can invent
 * their own groups; the well-known ones are available as constants.
 */
@JvmInline
value class TagGroup(val value: String) {
    companion object {
        val Board: TagGroup = TagGroup("board")
        val Level: TagGroup = TagGroup("level")
        val Subject: TagGroup = TagGroup("subject")
        val Paper: TagGroup = TagGroup("paper")
        val Difficulty: TagGroup = TagGroup("difficulty")
        val Custom: TagGroup = TagGroup("custom")

        val WellKnown: List<TagGroup> = listOf(Board, Level, Subject, Paper, Difficulty, Custom)
    }
}

val TopicId.isBuiltIn: Boolean get() = value.startsWith(Ids.BUILT_IN_PREFIX)
val CardId.isBuiltIn: Boolean get() = value.startsWith(Ids.BUILT_IN_PREFIX)
val TagId.isBuiltIn: Boolean get() = value.startsWith(Ids.BUILT_IN_PREFIX)

/**
 * Builds and parses the two id namespaces the app keeps strictly apart:
 *
 * - built-in content downloaded from the `content` branch:
 *   `builtin:<pack-id>:<topic-slug>` and `builtin:<pack-id>:<topic-slug>:<nnnn>`
 * - user content created in the app: `user:<uuid>`
 *
 * Built-in tags are deliberately *not* namespaced by pack (`builtin:tag:<group>:<slug>`)
 * because several packs legitimately share a tag such as `board = OCR`.
 * See docs/DECISIONS.md D13.
 */
object Ids {
    const val BUILT_IN_PREFIX: String = "builtin:"
    const val USER_PREFIX: String = "user:"
    private const val TAG_SEGMENT: String = "tag"

    fun builtInTopic(packId: PackId, topicSlug: String): TopicId =
        TopicId(BUILT_IN_PREFIX + packId.value + ":" + topicSlug)

    fun builtInCard(packId: PackId, topicSlug: String, ordinal: Int): CardId =
        CardId(BUILT_IN_PREFIX + packId.value + ":" + topicSlug + ":" + ordinal.toString().padStart(4, '0'))

    fun builtInTag(group: TagGroup, slug: String): TagId =
        TagId(BUILT_IN_PREFIX + TAG_SEGMENT + ":" + group.value + ":" + slug)

    fun userTopic(uuid: String): TopicId = TopicId(USER_PREFIX + uuid)

    fun userTag(uuid: String): TagId = TagId(USER_PREFIX + uuid)

    fun userCard(uuid: String): CardId = CardId(USER_PREFIX + uuid)

    /** The pack a built-in topic or card came from, or null for user content. */
    fun packIdOf(id: String): PackId? {
        if (!id.startsWith(BUILT_IN_PREFIX)) return null
        val rest = id.removePrefix(BUILT_IN_PREFIX)
        if (rest.startsWith("$TAG_SEGMENT:")) return null
        val separator = rest.indexOf(':')
        return if (separator <= 0) null else PackId(rest.substring(0, separator))
    }
}

private val HEX_DIGITS: CharArray = "0123456789abcdef".toCharArray()

/**
 * A version-4 style UUID generated from [random]. Written by hand because
 * `java.util.UUID` is JVM-only and no cryptographic strength is needed: these
 * ids only have to be unique within one user's local database.
 */
fun randomUuid(random: Random = Random.Default): String {
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = StringBuilder(36)
    for (index in bytes.indices) {
        val unsigned = bytes[index].toInt() and 0xff
        hex.append(HEX_DIGITS[unsigned shr 4]).append(HEX_DIGITS[unsigned and 0x0f])
        if (index == 3 || index == 5 || index == 7 || index == 9) hex.append('-')
    }
    return hex.toString()
}

/** `circular-motion` from `Circular motion`, used to build namespaced ids. */
fun slugify(input: String): String {
    val builder = StringBuilder(input.length)
    var previousWasSeparator = true
    for (character in input.lowercase()) {
        when {
            character in 'a'..'z' || character in '0'..'9' -> {
                builder.append(character)
                previousWasSeparator = false
            }

            else -> {
                if (!previousWasSeparator) builder.append('-')
                previousWasSeparator = true
            }
        }
    }
    return builder.toString().trim('-').ifEmpty { "topic" }
}
