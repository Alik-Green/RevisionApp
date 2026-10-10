package com.revisionapp.domain.progression

import com.revisionapp.data.AppJson
import kotlinx.datetime.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
        var progress = ProgressionRules.forToday(LearnerProgress(), today)
        val selectedQuests = ProgressionRules.dailyQuests(progress)
        assertEquals(3, selectedQuests.size)

        val firstLessonReward = selectedQuests.firstOrNull { it.id == "first-lesson" }?.rewardCoins ?: 0L
        val firstLesson = ProgressionRules.completeLesson(progress, "tmua", "lesson-1", today)
        progress = firstLesson.progress
        assertEquals(firstLessonReward + 12L, firstLesson.coinsEarned) // selected quest + first-lesson milestone

        val twoLessonsReward = selectedQuests.firstOrNull { it.id == "two-lessons" }?.rewardCoins ?: 0L
        val secondLesson = ProgressionRules.completeLesson(progress, "tmua", "lesson-2", today)
        progress = secondLesson.progress
        assertEquals(twoLessonsReward, secondLesson.coinsEarned)

        val tenAnswers = (1..10).fold(progress) { current, _ ->
            ProgressionRules.answerQuestion(current, today).progress
        }
        assertEquals(3, ProgressionRules.dailyQuests(tenAnswers).size)
        assertTrue(ProgressionRules.dailyQuests(tenAnswers).all { it.isClaimed })
        assertTrue(ProgressionRules.weeklyQuests(tenAnswers).any { it.id == "weekly-recall-streak" && it.isClaimed })

        val extraLesson = ProgressionRules.completeLesson(tenAnswers, "tmua", "lesson-3", today)
        assertEquals(0L, extraLesson.coinsEarned)
        assertEquals(tenAnswers.coins, extraLesson.progress.coins)
    }

    @Test
    fun incorrectAnswersCountAsAttemptsButDoNotEarnMasteryProgress() {
        val today = LocalDate(2026, 10, 10)
        var progress = LearnerProgress()
        repeat(5) { progress = ProgressionRules.answerQuestion(progress, today, isCorrect = false).progress }

        assertEquals(5, progress.totalQuestionsAnswered)
        assertEquals(0, progress.totalCorrectAnswers)
        assertEquals(0, progress.consecutiveCorrectAnswers)
        assertTrue(ProgressionRules.dailyQuests(progress).none { it.isComplete })
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
    fun v21AvatarUnlocksAndShapeSettingsMigrateToTheNewDesigner() {
        val legacyJson = """
            {
              "ownedAppearanceItemIds": [
                "skin-porcelain", "skin-fair", "skin-light", "skin-medium", "skin-deep", "skin-dark",
                "hair-short", "hair-long", "hair-curly", "hair-colour-blue",
                "eyes-classic", "eyes-almond", "eye-colour-violet", "nose-classic", "nose-straight"
              ],
              "characterAppearance": {
                "skinToneId": "skin-dark",
                "hairStyleId": "hair-curly",
                "hairColorId": "hair-colour-blue",
                "eyeStyleId": "eyes-almond",
                "eyeColorId": "eye-colour-violet",
                "noseStyleId": "nose-straight",
                "hairSize": 80,
                "hairHeight": 32,
                "eyeSize": 70,
                "eyeSpacing": 75,
                "eyeHeight": 45,
                "noseSize": 60,
                "noseHeight": 55
              }
            }
        """.trimIndent()
        val legacy = AppJson.instance.decodeFromString<LearnerProgress>(legacyJson)

        val migrated = ProgressionRules.migrateLegacyAppearance(legacy)

        assertTrue(migrated.ownedAppearanceItemIds.isEmpty())
        assertTrue("hair-long" in migrated.ownedAvatarPartIds)
        assertTrue("hair-blue" in migrated.ownedAvatarPartIds)
        assertTrue("eyes-almond" in migrated.ownedAvatarPartIds)
        assertTrue("nose-bridge" in migrated.ownedAvatarPartIds)
        assertEquals("skin-deep", migrated.characterAppearance.skinToneId)
        assertEquals("hair-curly", migrated.characterAppearance.hairStyleId)
        assertEquals("hair-blue", migrated.characterAppearance.hairColorId)
        assertEquals("eyes-violet", migrated.characterAppearance.eyeColorId)
        assertEquals("nose-bridge", migrated.characterAppearance.noseStyleId)
        assertEquals(0.8f, migrated.characterAppearance.hairSize)
        assertEquals(0.32f, migrated.characterAppearance.hairHeight)
        assertEquals(0.75f, migrated.characterAppearance.eyeSpacing)
    }

    @Test
    fun avatarStartsWithEveryNaturalSkinToneButOnlyStarterFeatures() {
        val progress = LearnerProgress()
        val owned = progress.ownedAvatarPartIds.toSet()

        assertEquals(8, AvatarPartCatalog.naturalSkinToneIds.size)
        assertTrue(AvatarPartCatalog.naturalSkinToneIds.all { it in owned })
        assertFalse("skin-mint" in owned)
        assertEquals(2, AvatarPartCatalog.inCategory(AvatarPartCategory.HAIR_STYLE).count { it.id in owned })
        assertEquals(1, AvatarPartCatalog.inCategory(AvatarPartCategory.EYE_STYLE).count { it.id in owned })
        assertEquals(1, AvatarPartCatalog.inCategory(AvatarPartCategory.NOSE_STYLE).count { it.id in owned })
        assertEquals(AvatarPartCatalog.DEFAULT_HAIR_STYLE, progress.characterAppearance.hairStyleId)
    }

    @Test
    fun avatarPartsCostCoinsAndUnownedFeaturesCannotBeEquipped() {
        val starting = LearnerProgress(coins = 100)
        val purchase = ProgressionRules.unlockAvatarPart(starting, "hair-curly")
        assertEquals(75L, purchase.coinsSpent)
        assertEquals(25L, purchase.progress.coins)
        assertTrue("hair-curly" in purchase.progress.ownedAvatarPartIds)

        val shaped = ProgressionRules.setCharacterAppearance(
            purchase.progress,
            CharacterAppearance(hairStyleId = "hair-curly", eyeSpacing = 1.5f, eyeSize = -0.2f),
        )
        assertEquals("hair-curly", shaped.characterAppearance.hairStyleId)
        assertEquals(1f, shaped.characterAppearance.eyeSpacing)
        assertEquals(0f, shaped.characterAppearance.eyeSize)

        val lockedSelection = ProgressionRules.setCharacterAppearance(
            shaped,
            shaped.characterAppearance.copy(hairStyleId = "hair-long"),
        )
        assertEquals("hair-curly", lockedSelection.characterAppearance.hairStyleId)
        val unaffordable = ProgressionRules.unlockAvatarPart(LearnerProgress(coins = 50), "hair-curly")
        assertEquals(50L, unaffordable.progress.coins)
        assertFalse("hair-curly" in unaffordable.progress.ownedAvatarPartIds)
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

    @Test
    fun dailyBoardSelectsThreeQuestsAndPersistsTheDrawForTheDay() {
        val today = LocalDate(2026, 10, 10)
        val progress = ProgressionRules.forToday(LearnerProgress(), today)
        val encoded = AppJson.instance.encodeToString(progress)
        val restored = AppJson.instance.decodeFromString<LearnerProgress>(encoded)

        assertEquals(3, progress.daily.selectedQuestIds.size)
        assertEquals(progress.daily.selectedQuestIds, restored.daily.selectedQuestIds)
        assertEquals(progress.daily.selectedQuestIds, ProgressionRules.forToday(restored, today).daily.selectedQuestIds)
        assertEquals(3, ProgressionRules.dailyQuests(restored).size)

        val nextDay = ProgressionRules.forToday(restored, LocalDate(2026, 10, 11))
        assertEquals(3, nextDay.daily.selectedQuestIds.size)
        assertEquals(
            nextDay.daily.selectedQuestIds.toSet(),
            ProgressionRules.dailyQuests(nextDay).map { it.id }.toSet(),
        )
    }

    @Test
    fun developerModeIsPersistedAsAnUnlimitedBalanceAndRestoresThePriorCoinBalance() {
        val starting = LearnerProgress(coins = 245)
        val enabled = ProgressionRules.setDeveloperMode(starting, enabled = true)

        assertTrue(enabled.developerMode)
        assertEquals(Long.MAX_VALUE, enabled.coins)
        assertEquals(AvatarPartCatalog.all.map { it.id }.toSet(), enabled.ownedAvatarPartIds.toSet())

        val afterReward = ProgressionRules.answerQuestion(enabled, LocalDate(2026, 10, 10)).progress
        assertEquals(Long.MAX_VALUE, afterReward.coins)
        assertTrue(afterReward.developerMode)

        val disabled = ProgressionRules.setDeveloperMode(afterReward, enabled = false)
        assertFalse(disabled.developerMode)
        assertEquals(245L, disabled.coins)
        assertNull(disabled.coinsBeforeDeveloperMode)
    }

    @Test
    fun faceShapeSlidersAreClampedAndSavedWithTheAppearance() {
        val shaped = ProgressionRules.setCharacterAppearance(
            LearnerProgress(),
            CharacterAppearance(faceWidth = 1.4f, faceHeight = -0.2f, faceRoundness = 0.73f),
        )

        assertEquals(1f, shaped.characterAppearance.faceWidth)
        assertEquals(0f, shaped.characterAppearance.faceHeight)
        assertEquals(0.73f, shaped.characterAppearance.faceRoundness)
    }
}
