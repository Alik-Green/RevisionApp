package com.revisionapp.domain.usecase

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeeklyQuestCalculatorTest {

    @Test
    fun questProgressUsesDistinctReviewDaysAndMondayBasedWeeks() {
        val result = WeeklyQuestCalculator.calculate(
            reviewDays = listOf(
                "2026-09-28", // previous week's five study days earn all rewards
                "2026-09-29",
                "2026-09-30",
                "2026-10-01",
                "2026-10-02",
                "2026-10-05", // current week: three distinct days
                "2026-10-07",
                "2026-10-09",
                "2026-10-09", // duplicates do not increase progress
                "not-a-date",
                "2026-10-11", // future review dates are ignored
            ),
            today = LocalDate(2026, 10, 10),
        )

        assertEquals(3, result.studiedDaysThisWeek)
        assertEquals(110, result.totalPoints)
        assertEquals(3, result.quests.size)
        assertTrue(result.quests[0].isComplete)
        assertTrue(result.quests[1].isComplete)
        assertFalse(result.quests[2].isComplete)
        assertEquals(1f, result.quests[0].progressFraction)
        assertEquals(0.6f, result.quests[2].progressFraction)
    }

    @Test
    fun questProgressResetsAtMondayButHistoricalPointsRemain() {
        val result = WeeklyQuestCalculator.calculate(
            reviewDays = listOf("2026-10-05", "2026-10-07", "2026-10-09"),
            today = LocalDate(2026, 10, 12),
        )

        assertEquals(0, result.studiedDaysThisWeek)
        assertEquals(30, result.totalPoints)
        assertTrue(result.quests.none { it.isComplete })
    }
}
