package com.revisionapp.domain.sync

/**
 * The layout of a sync location, as paths relative to its root.
 *
 * Kept as pure string building in the domain so that every provider, the zip
 * bundle format and the tests all agree on one shape, and so that changing it
 * is a one-file change rather than a protocol negotiation.
 *
 * ```
 * manifest/sync.json
 * records/card/a3/a3f1....json
 * records/card-state/9c/9c02....json
 * log/7f3ab21c/2026-10.jsonl
 * ```
 */
object SyncLayout {

    /**
     * Version of the record format this build writes and accepts. A remote
     * manifest above it is refused outright -- see [SyncFailure.SchemaTooNew].
     */
    const val SCHEMA_VERSION: Int = 1

    const val MANIFEST_PATH: String = "manifest/sync.json"

    /** Two characters is enough to keep a folder under a few thousand entries. */
    private const val SHARD_LENGTH: Int = 2

    private val SAFE_CHARACTERS: Set<Char> =
        (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_', '.')).toSet()

    fun recordPath(type: RecordType, id: RecordId): String =
        "records/${type.wire}/${shardOf(id)}/${escapeSegment(id.value)}.json"

    /**
     * The shard a record lives in: the first two characters of its escaped id.
     * Derived from the id rather than random, so the same record always maps to
     * the same path on every device and a listing can be split by prefix.
     */
    fun shardOf(id: RecordId): String =
        escapeSegment(id.value).take(SHARD_LENGTH).ifEmpty { "__" }

    fun logSegmentPath(deviceId: DeviceId, segment: LogSegmentName): String =
        "log/${escapeSegment(deviceId.value)}/${escapeSegment(segment.value)}.jsonl"

    /**
     * The segment a review belongs to: one file per device per month.
     *
     * Monthly rather than one file per device because segments are append-only
     * and never rewritten -- a single ever-growing file would have to be
     * re-uploaded in full after every review, which is the one thing a folder
     * watched by a cloud client handles worst.
     */
    fun segmentFor(year: Int, month: Int): LogSegmentName =
        LogSegmentName(year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0'))

    /**
     * Makes an id safe to use as a single path segment.
     *
     * Not cosmetic: user ids are `user:<uuid>` and composite ids contain `|`,
     * and `:` is illegal in a Windows filename. Every character outside
     * [SAFE_CHARACTERS] becomes `%XX` (or `%uXXXX` above U+00FF), and `%`
     * itself is escaped, so the mapping is reversible by [unescapeSegment].
     */
    fun escapeSegment(raw: String): String {
        // An empty segment would collapse two slashes into one and change the
        // depth of the path. Ids are never empty in this app, so the degenerate
        // case is mapped to a placeholder rather than modelled.
        if (raw.isEmpty()) return EMPTY_SEGMENT

        val escaped = buildString(raw.length) {
            for (character in raw) {
                if (character in SAFE_CHARACTERS) {
                    append(character)
                } else {
                    append('%')
                    if (character.code <= MAX_SINGLE_BYTE) {
                        append(character.code.toString(RADIX).padStart(2, '0'))
                    } else {
                        append('u')
                        append(character.code.toString(RADIX).padStart(4, '0'))
                    }
                }
            }
        }
        // "." and ".." are built entirely from safe characters but would climb
        // out of the shard directory, so their dots are encoded as well. Every
        // branch here is reversed exactly by unescapeSegment.
        return when (escaped) {
            "." -> "%2e"
            ".." -> "%2e%2e"
            else -> escaped
        }
    }

    /**
     * Inverse of [escapeSegment]. A malformed escape -- a `%` that is not
     * followed by the right number of hex digits -- is passed through verbatim
     * rather than throwing, because a listing may include a file this build did
     * not write and one odd name must not abort a sync.
     */
    fun unescapeSegment(escaped: String): String {
        val out = StringBuilder(escaped.length)
        var index = 0
        while (index < escaped.length) {
            val character = escaped[index]
            if (character != '%') {
                out.append(character)
                index++
                continue
            }
            val wide = index + 1 < escaped.length && escaped[index + 1] == 'u'
            val digitsStart = if (wide) index + 2 else index + 1
            val digitCount = if (wide) 4 else 2
            val digitsEnd = digitsStart + digitCount
            val digits = if (digitsEnd <= escaped.length) escaped.substring(digitsStart, digitsEnd) else ""
            val code = digits.toIntOrNull(RADIX)
            if (digits.length != digitCount || code == null) {
                out.append(character)
                index++
            } else {
                out.append(code.toChar())
                index = digitsEnd
            }
        }
        return out.toString()
    }

    private const val EMPTY_SEGMENT: String = "_"
    private const val RADIX: Int = 16
    private const val MAX_SINGLE_BYTE: Int = 0xFF
}
