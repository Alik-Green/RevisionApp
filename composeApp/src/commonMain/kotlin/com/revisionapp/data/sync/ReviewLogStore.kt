package com.revisionapp.data.sync

import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.srs.Rating
import com.revisionapp.domain.sync.DeviceId
import com.revisionapp.domain.sync.LogSegmentName
import com.revisionapp.domain.sync.LoggedReview
import com.revisionapp.domain.sync.ReviewLogMerger
import com.revisionapp.domain.sync.SyncProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One line of a `.jsonl` segment.
 *
 * Enums are carried as strings and resolved by hand rather than by the
 * serializer, so a line written by a newer build with a mode or a rating this
 * one has never heard of is skipped instead of failing the whole segment. A
 * review that cannot be understood must cost one review, not the history.
 */
@Serializable
private data class ReviewLogLine(
    val eventId: String,
    val cardId: String,
    val mode: String,
    val rating: String,
    val verdict: String? = null,
    val correct: Boolean = false,
    val reviewedAt: Long = 0L,
    val deviceId: String,
    val escalation: Int = 0,
)

/** Translates between [LoggedReview] and the line format on disk. */
class ReviewLogCodec(private val json: Json) {

    fun encode(review: LoggedReview): String = json.encodeToString(
        ReviewLogLine.serializer(),
        ReviewLogLine(
            eventId = review.eventId,
            cardId = review.cardId.value,
            mode = review.mode.name,
            rating = review.rating.name,
            verdict = review.verdict?.name,
            correct = review.correct,
            reviewedAt = review.reviewedAt,
            deviceId = review.deviceId.value,
            escalation = review.escalation,
        ),
    )

    /** Null when a line cannot be understood. Never throws. */
    fun decode(line: String): LoggedReview? {
        if (line.isBlank()) return null
        return runCatching {
            val parsed = json.decodeFromString(ReviewLogLine.serializer(), line)
            val mode = StudyMode.entries.firstOrNull { it.name == parsed.mode } ?: return@runCatching null
            val rating = Rating.entries.firstOrNull { it.name == parsed.rating } ?: return@runCatching null
            LoggedReview(
                eventId = parsed.eventId,
                cardId = CardId(parsed.cardId),
                mode = mode,
                rating = rating,
                verdict = parsed.verdict?.let { raw -> VerdictKind.entries.firstOrNull { it.name == raw } },
                correct = parsed.correct,
                reviewedAt = parsed.reviewedAt,
                deviceId = DeviceId(parsed.deviceId),
                escalation = parsed.escalation,
            )
        }.getOrNull()
    }
}

/** What a pull found, including what it could not read. */
data class MergedHistory(
    val reviews: List<LoggedReview>,
    val devices: Int,
    val segments: Int,
    val duplicates: Int,
    val unreadable: Int,
) {
    companion object {
        val EMPTY: MergedHistory = MergedHistory(emptyList(), 0, 0, 0, 0)
    }
}

/**
 * Reads and appends review-log segments through a [SyncProvider].
 *
 * Segments are append-only and never rewritten, one file per device per month.
 * That choice is what makes the log survivable on the worst transport we
 * support -- a folder watched by a cloud desktop client -- because appending a
 * new month's file can never conflict with another device editing the same
 * bytes, and a partially copied segment loses its tail rather than its head.
 */
class ReviewLogStore(
    private val provider: SyncProvider,
    private val codec: ReviewLogCodec,
) {

    /** Every review from every device, deduplicated and in time order. */
    suspend fun mergedHistory(): MergedHistory {
        val devices = runCatching { provider.listLogDevices() }.getOrNull() ?: emptyList()
        val segments = mutableListOf<List<LoggedReview>>()
        var segmentCount = 0
        var unreadable = 0

        for (device in devices) {
            val names = runCatching { provider.listLogSegments(device) }.getOrNull() ?: emptyList()
            for (name in names) {
                val lines = runCatching { provider.readLogSegment(device, name) }.getOrNull() ?: continue
                segmentCount++
                val decoded = mutableListOf<LoggedReview>()
                for (line in lines) {
                    val review = codec.decode(line)
                    if (review == null) {
                        if (line.isNotBlank()) unreadable++
                    } else {
                        decoded += review
                    }
                }
                segments += decoded
            }
        }

        val merged = ReviewLogMerger.merge(segments)
        return MergedHistory(
            reviews = merged,
            devices = devices.size,
            segments = segmentCount,
            duplicates = ReviewLogMerger.duplicateCount(segments),
            unreadable = unreadable,
        )
    }

    /**
     * Appends this device's reviews to one segment.
     *
     * Returns false rather than throwing when the provider refuses: a review
     * that could not be uploaded is still recorded locally, and the next run
     * tries again.
     */
    suspend fun append(
        deviceId: DeviceId,
        segment: LogSegmentName,
        reviews: List<LoggedReview>,
    ): Boolean {
        if (reviews.isEmpty()) return true
        val lines = reviews.map { codec.encode(it) }
        return runCatching { provider.appendLogSegment(deviceId, segment, lines) }.isSuccess
    }
}
