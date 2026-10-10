package com.revisionapp.domain.progression

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressionRulesTest {
    @Test
    fun answeringQuestionsAdvancesTheStreakOnlyOncePerDay() {
        val today = LocalDate(2026, 10, 10)
        val first = ProgressionRules.answerQuestion(LearnerProgress(), today).progress
        val second = ProgressionRules.answerQuestion(first, today).progress

        assertEquals(1, first.streakDays)
        assertEquals(1, second.streakDays)
        assertEquals(2, second.daily.questionsAnswered)
        assertEquals(2, second.totalQuestionsAnswered)
    }

    @Test
    fun nextDayContinuesStreakAndGapCreatesRecoveryOffer() {
        val yesterday = LocalDate(2026, 10, 9)
        val today = LocalDate(2026, 10, 10)
        val continuing = ProgressionRules.answerQuestion(
            LearnerProgress(streakDays = 4, lastStudyDate = yesterday.toString()),
            today,
        ).progress
        assertEquals(5, continuing.streakDays)
        assertNull(continuing.streakRecovery)

        val afterGap = ProgressionRules.forToday(
            LearnerProgress(streakDays = 5, lastStudyDate = LocalDate(2026, 10, 6).toString()),
            today,
        )
        assertEquals(0, afterGap.streakDays)
        assertEquals(5, afterGap.streakRecovery?.streakDays)
        assertEquals(3, afterGap.streakRecovery?.missedDays)
    }

    @Test
    fun recoveryPriceFreezesAfterTheLearnerResumesStudying() {
        val today = LocalDate(2026, 10, 10)
        val broken = ProgressionRules.forToday(
            LearnerProgress(streakDays = 4, lastStudyDate = LocalDate(2026, 10, 8).toString()),
            today,
        )
        val resumed = ProgressionRules.answerQuestion(broken, today).progress
        val tomorrow = ProgressionRules.forToday(resumed, LocalDate(2026, 10, 11))

        assertEquals(1, tomorrow.streakDays)
        assertEquals(1, tomorrow.streakRecovery?.missedDays)
    }

    @Test
    fun recoveryPriceDoublesForEachAdditionalMissedDay() {
        assertEquals(25L, ProgressionRules.streakRecoveryCost(1))
        assertEquals(50L, ProgressionRules.streakRecoveryCost(2))
        assertEquals(100L, ProgressionRules.streakRecoveryCost(3))
    }

    @Test
    fun buyingBackRestoresTheOldStreakAndChargesCoins() {
        val today = LocalDate(2026, 10, 10)
        val broken = LearnerProgress(
            coins = 100,
            streakRecovery = StreakRecovery(
                streakDays = 8,
                lastStudyDate = LocalDate(2026, 10, 6).toString(),
                missedDays = 3,
            ),
        )

        val purchase = ProgressionRules.buyBackStreak(broken, today)

        assertEquals(8, purchase.progress.streakDays)
        assertEquals(0L, purchase.progress.coins)
        assertEquals(today.toString(), purchase.progress.lastStudyDate)
        assertNull(purchase.progress.streakRecovery)
    }

    @Test
    fun insufficientCoinsLeaveTheRecoveryOfferUntouched() {
        val today = LocalDate(2026, 10, 10)
        val recovery = StreakRecovery(4, LocalDate(2026, 10, 6).toString(), 3)
        val purchase = ProgressionRules.buyBackStreak(
            LearnerProgress(coins = 99, streakRecovery = recovery),
            today,
        )

        assertEquals(0, purchase.progress.streakDays)
        assertEquals(99L, purchase.progress.coins)
        assertEquals(recovery, purchase.progress.streakRecovery)
    }

    @Test
    fun questsAwardCoinsOnceWhenTheirTargetsAreReached() {
        val today = LocalDate(2026, 10, 10)
        var progress = LearnerProgress()
        val firstLesson = ProgressionRules.completeLesson(progress, "tmua", "lesson-1", today)
        progress = firstLesson.progress
        assertEquals(0L, firstLesson.coinsEarned)

        val secondLesson = ProgressionRules.completeLesson(progress, "tmua", "lesson-2", today)
        progress = secondLesson.progress
        assertEquals(30L, secondLesson.coinsEarned)
        assertEquals(30L, progress.coins)

        val tenAnswers = (1..10).fold(progress) { current, _ ->
            ProgressionRules.answerQuestion(current, today).progress
        }
        assertEquals(50L, tenAnswers.coins)
        assertTrue(ProgressionRules.dailyQuests(tenAnswers).all { it.isClaimed })

        val extraLesson = ProgressionRules.completeLesson(tenAnswers, "tmua", "lesson-3", today)
        assertEquals(0L, extraLesson.coinsEarned)
        assertEquals(50L, extraLesson.progress.coins)
    }

    @Test
    fun replayingACompletedLessonDoesNotDoubleCountItsRewardProgress() {
        val today = LocalDate(2026, 10, 10)
        val first = ProgressionRules.completeLesson(LearnerProgress(), "tmua", "intro", today).progress
        val replay = ProgressionRules.completeLesson(first, "tmua", "intro", today).progress

        assertEquals(1, replay.totalLessonsCompleted)
        assertEquals(1, replay.daily.lessonsCompleted)
        assertEquals(first.coins, replay.coins)
    }

    @Test
    fun dailyQuestCountersResetAtTheNextLocalDate() {
        val yesterday = LocalDate(2026, 10, 9)
        val today = LocalDate(2026, 10, 10)
        val priorDay = ProgressionRules.answerQuestion(LearnerProgress(), yesterday).progress
        val reset = ProgressionRules.forToday(priorDay, today)

        assertEquals(today.toString(), reset.daily.date)
        assertEquals(0, reset.daily.questionsAnswered)
        assertTrue(reset.streakDays == 1)
        assertFalse(ProgressionRules.dailyQuests(reset).any { it.isClaimed })
    }

    @Test
    fun completedLessonIdsAreCourseScoped() {
        val today = LocalDate(2026, 10, 10)
        val progress = ProgressionRules.completeLesson(LearnerProgress(), "tmua", "intro", today).progress
        assertTrue(ProgressionRules.hasCompleted(progress, "tmua", "intro"))
        assertFalse(ProgressionRules.hasCompleted(progress, "further-maths", "intro"))
    }
}
