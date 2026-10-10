package com.revisionapp.domain.usecase

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakCalculatorTest {

    private val today = LocalDate(2026, 10, 10)

    @Test
    fun countsConsecutiveDaysIncludingToday() {
        assertEquals(
            3,
            StreakCalculator.currentStreak(
                listOf("2026-10-08", "2026-10-09", "2026-10-10", "2026-10-10"),
                today,
            ),
        )
    }

    @Test
    fun yesterdayKeepsTheStreakAliveButAnOlderGapDoesNot() {
        assertEquals(2, StreakCalculator.currentStreak(listOf("2026-10-08", "2026-10-09"), today))
        assertEquals(0, StreakCalculator.currentStreak(listOf("2026-10-07", "2026-10-08"), today))
    }
}
