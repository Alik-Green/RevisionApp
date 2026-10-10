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
        assertEquals(20L, firstLesson.coinsEarned) // first-step quest + first-lesson milestone

        val secondLesson = ProgressionRules.completeLesson(progress, "tmua", "lesson-2", today)
        progress = secondLesson.progress
        assertEquals(12L, secondLesson.coinsEarned)
        assertEquals(32L, progress.coins)

        val tenAnswers = (1..10).fold(progress) { current, _ ->
            ProgressionRules.answerQuestion(current, today).progress
        }
        assertEquals(162L, tenAnswers.coins)
        assertTrue(ProgressionRules.dailyQuests(tenAnswers).all { it.isClaimed })
        assertTrue(ProgressionRules.weeklyQuests(tenAnswers).any { it.id == "weekly-recall-streak" && it.isClaimed })

        val extraLesson = ProgressionRules.completeLesson(tenAnswers, "tmua", "lesson-3", today)
        assertEquals(0L, extraLesson.coinsEarned)
        assertEquals(162L, extraLesson.progress.coins)
    }

    @Test
    fun incorrectAnswersCountAsAttemptsButDoNotEarnMasteryProgress() {
        val today = LocalDate(2026, 10, 10)
        var progress = LearnerProgress()
        repeat(5) { progress = ProgressionRules.answerQuestion(progress, today, isCorrect = false).progress }

        assertEquals(5, progress.totalQuestionsAnswered)
        assertEquals(0, progress.totalCorrectAnswers)
        assertEquals(0, progress.consecutiveCorrectAnswers)
        assertFalse(ProgressionRules.dailyQuests(progress).first { it.id == "five-correct" }.isComplete)
        assertFalse("five-correct-answers" in progress.unlockedAchievementIds)
    }

    @Test
    fun studyDayAndWeeklyQuestsUseDistinctDatesAndResetOnMonday() {
        var progress = LearnerProgress()
        val friday = LocalDate(2026, 10, 9)
        progress = ProgressionRules.answerQuestion(progress, friday, isCorrect = true).progress
        progress = ProgressionRules.answerQuestion(progress, friday, isCorrect = true).progress
        progress = ProgressionRules.answerQuestion(progress, LocalDate(2026, 10, 10), isCorrect = true).progress
        val sunday = ProgressionRules.answerQuestion(progress, LocalDate(2026, 10, 11), isCorrect = true).progress

        assertEquals(3, sunday.totalStudyDays)
        assertEquals(3, sunday.weekly.studyDates.distinct().size)
        assertTrue(ProgressionRules.weeklyQuests(sunday).first { it.id == "weekly-rhythm-three-days" }.isClaimed)
        val monday = ProgressionRules.forToday(sunday, LocalDate(2026, 10, 12))
        assertTrue(monday.weekly.studyDates.isEmpty())
        assertTrue(ProgressionRules.weeklyQuests(monday).none { it.isClaimed })
        assertTrue(monday.coins >= sunday.coins)
    }

    @Test
    fun achievementsUnlockFromLearningMilestonesAndPayOnlyOnce() {
        val today = LocalDate(2026, 10, 10)
        var progress = LearnerProgress()
        for (lesson in 1..5) {
            progress = ProgressionRules.completeLesson(progress, "tmua", "lesson-$lesson", today).progress
        }
        assertTrue("first-lesson" in progress.unlockedAchievementIds)
        assertTrue("five-lessons" in progress.unlockedAchievementIds)
        val earned = progress.coins

        val secondCheck = ProgressionRules.claimAvailableRewards(progress)
        assertEquals(0L, secondCheck.coinsEarned)
        assertEquals(earned, secondCheck.progress.coins)
        assertTrue(ProgressionRules.achievements(secondCheck.progress).first { it.id == "five-lessons" }.isUnlocked)
    }

    @Test
    fun starterCharacterHasTheNaturalSkinRangeAndOnlyStarterFeatureChoices() {
        val progress = LearnerProgress()
        val owned = progress.ownedAppearanceItemIds.toSet()

        assertEquals(6, AppearanceCatalog.inCategory(AppearanceCategory.SKIN_TONE).count { it.id in owned && it.cost == 0L })
        assertEquals(2, AppearanceCatalog.inCategory(AppearanceCategory.HAIR_STYLE).count { it.id in owned })
        assertEquals(1, AppearanceCatalog.inCategory(AppearanceCategory.EYE_STYLE).count { it.id in owned })
        assertEquals(1, AppearanceCatalog.inCategory(AppearanceCategory.NOSE_STYLE).count { it.id in owned })
        assertEquals(AppearanceCatalog.SKIN_MEDIUM, progress.characterAppearance.skinToneId)
    }

    @Test
    fun cosmeticPiecesAreEarnedOnceAndShapeControlsStayFree() {
        val starting = LearnerProgress(coins = 100)
        val purchase = ProgressionRules.unlockAppearanceItem(starting, "hair-wavy")
        assertEquals(40L, purchase.coinsSpent)
        assertEquals(60L, purchase.progress.coins)
        assertTrue("hair-wavy" in purchase.progress.ownedAppearanceItemIds)
        assertEquals(AppearanceCatalog.HAIR_SHORT, purchase.progress.characterAppearance.hairStyleId)

        val selected = ProgressionRules.selectAppearanceItem(purchase.progress, "hair-wavy")
        assertEquals("hair-wavy", selected.characterAppearance.hairStyleId)
        val tuned = ProgressionRules.updateCharacterAppearance(
            selected,
            selected.characterAppearance.copy(eyeSpacing = 84, eyeSize = 72, noseHeight = 63),
        )
        assertEquals(84, tuned.characterAppearance.eyeSpacing)
        assertEquals(72, tuned.characterAppearance.eyeSize)
        assertEquals(63, tuned.characterAppearance.noseHeight)
        assertEquals(selected.coins, tuned.coins)

        val repeated = ProgressionRules.unlockAppearanceItem(tuned, "hair-wavy")
        assertEquals(0L, repeated.coinsSpent)
        assertEquals(60L, repeated.progress.coins)
        assertEquals(tuned, repeated.progress)
        val lockedSelection = ProgressionRules.selectAppearanceItem(tuned, "eyes-round")
        assertEquals(tuned.characterAppearance.eyeStyleId, lockedSelection.characterAppearance.eyeStyleId)
        val unaffordable = ProgressionRules.unlockAppearanceItem(tuned, "skin-sky")
        assertEquals(tuned, unaffordable.progress)
        assertEquals(0L, unaffordable.coinsSpent)
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
