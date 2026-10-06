package com.revisionapp.domain.usecase

import kotlinx.datetime.LocalDate

/**
 * Consecutive-day streaks, counted over epoch-day numbers so that no calendar
 * arithmetic (time zones, month lengths, leap years) is involved.
 *
 * A streak survives one day without a review only at the very start: if nothing
 * was reviewed today but something was reviewed yesterday it is still alive, and
 * it ends when today passes without a review.
 */
object StreakCalculator {

    /** @param reviewDays ISO local dates (`yyyy-MM-dd`) that had at least one review. */
    fun currentStreak(reviewDays: Collection<String>, today: LocalDate): Int {
        val days = reviewDayNumbers(reviewDays)
        if (days.isEmpty()) return 0

        val todayNumber = today.toEpochDays()
        var cursor = when {
            todayNumber in days -> todayNumber
            (todayNumber - 1) in days -> todayNumber - 1
            else -> return 0
        }

        var streak = 0
        while (cursor in days) {
            streak++
            cursor--
        }
        return streak
    }

    fun daysStudied(reviewDays: Collection<String>): Int = reviewDayNumbers(reviewDays).size

    private fun reviewDayNumbers(reviewDays: Collection<String>): Set<Long> =
        reviewDays.mapNotNull { parseDate(it)?.toEpochDays() }.toSet()

    private fun parseDate(raw: String): LocalDate? {
        val trimmed = raw.trim()
        if (trimmed.length != ISO_DATE_LENGTH) return null
        return runCatching { LocalDate.parse(trimmed) }.getOrNull()
    }

    private const val ISO_DATE_LENGTH = 10
}
