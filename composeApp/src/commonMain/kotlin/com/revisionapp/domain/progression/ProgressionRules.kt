package com.revisionapp.domain.progression

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class DailyProgress(
    val date: String = "",
    val lessonsCompleted: Int = 0,
    val questionsAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val bestCorrectAnswerStreak: Int = 0,
    val claimedQuestIds: List<String> = emptyList(),
    /** The three quests drawn for this date; persisted so reloads never reroll the board. */
    val selectedQuestIds: List<String> = emptyList(),
)

@Serializable
data class WeeklyProgress(
    /** ISO-week Monday as an epoch-day string. */
    val weekId: String = "",
    val lessonsCompleted: Int = 0,
    val questionsAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val bestCorrectAnswerStreak: Int = 0,
    val studyDates: List<String> = emptyList(),
    val claimedQuestIds: List<String> = emptyList(),
)

@Serializable
data class StreakRecovery(
    val streakDays: Int,
    val lastStudyDate: String,
    val missedDays: Int,
)

/** Local-first progression. It is separate from card scheduling and review history. */
@Serializable
data class LearnerProgress(
    val displayName: String = "Learner",
    val activeCourseId: String = "",
    val coins: Long = 0,
    val developerMode: Boolean = false,
    /** Saved balance restored when developer mode is turned off. */
    val coinsBeforeDeveloperMode: Long? = null,
    val streakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val lastStudyDate: String? = null,
    val streakRecovery: StreakRecovery? = null,
    val completedLessonIds: List<String> = emptyList(),
    val totalLessonsCompleted: Int = 0,
    val totalQuestionsAnswered: Int = 0,
    val totalCorrectAnswers: Int = 0,
    val consecutiveCorrectAnswers: Int = 0,
    val bestCorrectAnswerStreak: Int = 0,
    val totalStudyDays: Int = 0,
    val unlockedAchievementIds: List<String> = emptyList(),
    /** v2.1 field retained only to migrate existing cosmetic unlocks on startup. */
    val ownedAppearanceItemIds: List<String> = emptyList(),
    val characterAppearance: CharacterAppearance = CharacterAppearance(),
    val ownedAvatarPartIds: List<String> = AvatarPartCatalog.startingOwnedIds,
    val daily: DailyProgress = DailyProgress(),
    val weekly: WeeklyProgress = WeeklyProgress(),
) {
    val coinBalanceLabel: String get() = if (developerMode) "∞" else coins.toString()
}

data class DailyQuestProgress(
    val id: String,
    val title: String,
    val description: String,
    val current: Int,
    val target: Int,
    val rewardCoins: Long,
    val isClaimed: Boolean,
) {
    val isComplete: Boolean get() = current >= target
    val progressFraction: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
}

data class AchievementProgress(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val current: Int,
    val target: Int,
    val rewardCoins: Long,
    val isUnlocked: Boolean,
) {
    val progressFraction: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
}

data class ProgressionMutation(
    val progress: LearnerProgress,
    val coinsEarned: Long = 0,
    val coinsSpent: Long = 0,
)

/** Pure rules for learning quests, mastery milestones, coins and streak recovery. */
object ProgressionRules {
    private data class QuestDefinition(
        val id: String,
        val title: String,
        val description: String,
        val target: Int,
        val rewardCoins: Long,
        val current: (DailyProgress) -> Int,
    )

    private data class WeeklyQuestDefinition(
        val id: String,
        val title: String,
        val description: String,
        val target: Int,
        val rewardCoins: Long,
        val current: (WeeklyProgress) -> Int,
    )

    private data class AchievementDefinition(
        val id: String,
        val title: String,
        val description: String,
        val icon: String,
        val target: Int,
        val rewardCoins: Long,
        val current: (LearnerProgress) -> Int,
    )

    private val questDefinitions = listOf(
        QuestDefinition(
            id = "first-lesson",
            title = "First step",
            description = "Complete 1 lesson today",
            target = 1,
            rewardCoins = 8,
            current = DailyProgress::lessonsCompleted,
        ),
        QuestDefinition(
            id = "two-lessons",
            title = "Lesson rhythm",
            description = "Complete 2 lessons today",
            target = 2,
            rewardCoins = 12,
            current = DailyProgress::lessonsCompleted,
        ),
        QuestDefinition(
            id = "ten-questions",
            title = "Thoughtful practice",
            description = "Answer 10 questions and get at least 5 right today",
            target = 1,
            rewardCoins = 8,
            current = { daily ->
                if (daily.questionsAnswered >= 10 && daily.correctAnswers >= 5) 1 else 0
            },
        ),
        QuestDefinition(
            id = "five-correct",
            title = "Confident recall",
            description = "Get 5 answers right today",
            target = 5,
            rewardCoins = 10,
            current = DailyProgress::correctAnswers,
        ),
        QuestDefinition(
            id = "recall-streak-three",
            title = "Focused recall",
            description = "Get 3 answers right in a row today",
            target = 3,
            rewardCoins = 10,
            current = DailyProgress::bestCorrectAnswerStreak,
        ),
        QuestDefinition(
            id = "balanced-session",
            title = "Learn and practise",
            description = "Complete a lesson and get 5 answers right today",
            target = 1,
            rewardCoins = 12,
            current = { daily ->
                if (daily.lessonsCompleted >= 1 && daily.correctAnswers >= 5) 1 else 0
            },
        ),
    )

    private val weeklyQuestDefinitions = listOf(
        WeeklyQuestDefinition(
            id = "weekly-rhythm-three-days",
            title = "Build a rhythm",
            description = "Study on 3 different days this week",
            target = 3,
            rewardCoins = 20,
            current = { it.studyDates.distinct().size },
        ),
        WeeklyQuestDefinition(
            id = "weekly-four-lessons",
            title = "Pathfinder",
            description = "Complete 4 lessons this week",
            target = 4,
            rewardCoins = 20,
            current = WeeklyProgress::lessonsCompleted,
        ),
        WeeklyQuestDefinition(
            id = "weekly-recall-streak",
            title = "Sharp recall",
            description = "Get 5 answers right in a row this week",
            target = 5,
            rewardCoins = 25,
            current = WeeklyProgress::bestCorrectAnswerStreak,
        ),
        WeeklyQuestDefinition(
            id = "weekly-balanced-practice",
            title = "Steady and sharp",
            description = "Study 3 days, finish 3 lessons, and get 15 answers right",
            target = 1,
            rewardCoins = 30,
            current = { week ->
                if (week.studyDates.distinct().size >= 3 && week.lessonsCompleted >= 3 && week.correctAnswers >= 15) {
                    1
                } else {
                    0
                }
            },
        ),
    )

    private val achievementDefinitions = listOf(
        AchievementDefinition(
            id = "first-lesson",
            title = "First steps",
            description = "Complete your first lesson.",
            icon = "🌟",
            target = 1,
            rewardCoins = 12,
            current = LearnerProgress::totalLessonsCompleted,
        ),
        AchievementDefinition(
            id = "five-lessons",
            title = "Finding your pace",
            description = "Complete 5 lessons.",
            icon = "🧭",
            target = 5,
            rewardCoins = 25,
            current = LearnerProgress::totalLessonsCompleted,
        ),
        AchievementDefinition(
            id = "twenty-lessons",
            title = "Path explorer",
            description = "Complete 20 lessons.",
            icon = "🏕️",
            target = 20,
            rewardCoins = 55,
            current = LearnerProgress::totalLessonsCompleted,
        ),
        AchievementDefinition(
            id = "fifty-lessons",
            title = "Course companion",
            description = "Complete 50 lessons.",
            icon = "🏆",
            target = 50,
            rewardCoins = 100,
            current = LearnerProgress::totalLessonsCompleted,
        ),
        AchievementDefinition(
            id = "five-correct-answers",
            title = "Confident recall",
            description = "Get 5 answers right.",
            icon = "💡",
            target = 5,
            rewardCoins = 12,
            current = LearnerProgress::totalCorrectAnswers,
        ),
        AchievementDefinition(
            id = "fifty-correct-answers",
            title = "Knowledge taking root",
            description = "Get 50 answers right.",
            icon = "🌿",
            target = 50,
            rewardCoins = 40,
            current = LearnerProgress::totalCorrectAnswers,
        ),
        AchievementDefinition(
            id = "five-in-a-row",
            title = "Five in a row",
            description = "Build a streak of 5 correct answers in one practice run.",
            icon = "🎯",
            target = 5,
            rewardCoins = 18,
            current = LearnerProgress::bestCorrectAnswerStreak,
        ),
        AchievementDefinition(
            id = "ten-in-a-row",
            title = "Unbroken focus",
            description = "Build a streak of 10 correct answers in one practice run.",
            icon = "✨",
            target = 10,
            rewardCoins = 35,
            current = LearnerProgress::bestCorrectAnswerStreak,
        ),
        AchievementDefinition(
            id = "five-study-days",
            title = "A habit begins",
            description = "Study on 5 different days.",
            icon = "🌱",
            target = 5,
            rewardCoins = 25,
            current = LearnerProgress::totalStudyDays,
        ),
        AchievementDefinition(
            id = "twenty-study-days",
            title = "Steady learner",
            description = "Study on 20 different days.",
            icon = "📚",
            target = 20,
            rewardCoins = 60,
            current = LearnerProgress::totalStudyDays,
        ),
        AchievementDefinition(
            id = "three-day-streak",
            title = "Three-day rhythm",
            description = "Study on 3 consecutive days.",
            icon = "🔥",
            target = 3,
            rewardCoins = 20,
            current = LearnerProgress::bestStreakDays,
        ),
        AchievementDefinition(
            id = "seven-day-streak",
            title = "A week of learning",
            description = "Study on 7 consecutive days.",
            icon = "🌈",
            target = 7,
            rewardCoins = 45,
            current = LearnerProgress::bestStreakDays,
        ),
    )

    /** Reset daily/weekly counters and surface a lost streak without erasing it. */
    fun forToday(progress: LearnerProgress, today: LocalDate): LearnerProgress {
        val date = today.toString()
        val daily = if (progress.daily.date == date) {
            if (progress.daily.selectedQuestIds.isEmpty()) {
                progress.daily.copy(selectedQuestIds = selectDailyQuestIds(date))
            } else {
                progress.daily
            }
        } else {
            DailyProgress(date = date, selectedQuestIds = selectDailyQuestIds(date))
        }
        val weekId = weekId(today)
        val weekly = if (progress.weekly.weekId == weekId) progress.weekly else WeeklyProgress(weekId = weekId)
        var streak = progress.streakDays
        var recovery = progress.streakRecovery

        val gap = progress.lastStudyDate?.let { daysBetween(it, today) }
        if (gap != null && gap > 1) {
            if (recovery == null && streak > 0) {
                recovery = StreakRecovery(
                    streakDays = streak,
                    lastStudyDate = progress.lastStudyDate,
                    missedDays = (gap - 1).toInt(),
                )
            }
            streak = 0
        }
        recovery = recovery?.let { lost ->
            // A resumed study day freezes the repair price at the original gap.
            if (progress.lastStudyDate != lost.lastStudyDate) {
                lost
            } else {
                val lostGap = daysBetween(lost.lastStudyDate, today)
                if (lostGap == null || lostGap <= 1) {
                    lost
                } else {
                    lost.copy(missedDays = (lostGap - 1).toInt())
                }
            }
        }
        return progress.copy(daily = daily, weekly = weekly, streakDays = streak, streakRecovery = recovery)
    }

    /** Count one answered V2 question, record whether recall was correct, and credit a study day. */
    fun answerQuestion(
        progress: LearnerProgress,
        today: LocalDate,
        isCorrect: Boolean = true,
    ): ProgressionMutation {
        val current = forToday(progress, today)
        val active = recordStudyDay(current, today)
        val previousCorrectStreak = if (active.daily.questionsAnswered == 0) 0 else active.consecutiveCorrectAnswers
        val correctStreak = if (isCorrect) previousCorrectStreak + 1 else 0
        val updated = active.copy(
            totalQuestionsAnswered = active.totalQuestionsAnswered + 1,
            totalCorrectAnswers = active.totalCorrectAnswers + if (isCorrect) 1 else 0,
            consecutiveCorrectAnswers = correctStreak,
            bestCorrectAnswerStreak = maxOf(active.bestCorrectAnswerStreak, correctStreak),
            daily = active.daily.copy(
                questionsAnswered = active.daily.questionsAnswered + 1,
                correctAnswers = active.daily.correctAnswers + if (isCorrect) 1 else 0,
                bestCorrectAnswerStreak = maxOf(active.daily.bestCorrectAnswerStreak, correctStreak),
            ),
            weekly = active.weekly.copy(
                questionsAnswered = active.weekly.questionsAnswered + 1,
                correctAnswers = active.weekly.correctAnswers + if (isCorrect) 1 else 0,
                bestCorrectAnswerStreak = maxOf(active.weekly.bestCorrectAnswerStreak, correctStreak),
            ),
        )
        return awardNewRewards(updated)
    }

    /** Completing a lesson unlocks its course-path successors and advances the streak. */
    fun completeLesson(
        progress: LearnerProgress,
        courseId: String,
        lessonId: String,
        today: LocalDate,
    ): ProgressionMutation {
        val current = forToday(progress, today)
        val fullId = "$courseId:$lessonId"
        if (fullId in current.completedLessonIds) return ProgressionMutation(current)
        val active = recordStudyDay(current, today)
        val updated = active.copy(
            completedLessonIds = (active.completedLessonIds + fullId).distinct(),
            totalLessonsCompleted = active.totalLessonsCompleted + 1,
            daily = active.daily.copy(lessonsCompleted = active.daily.lessonsCompleted + 1),
            weekly = active.weekly.copy(lessonsCompleted = active.weekly.lessonsCompleted + 1),
        )
        return awardNewRewards(updated)
    }

    fun setActiveCourse(progress: LearnerProgress, courseId: String): LearnerProgress =
        progress.copy(activeCourseId = courseId)

    fun setDisplayName(progress: LearnerProgress, displayName: String): LearnerProgress =
        progress.copy(displayName = displayName.trim().take(MAX_DISPLAY_NAME_LENGTH).ifBlank { "Learner" })

    /** Local developer mode grants every current cosmetic and restores the prior balance when disabled. */
    fun setDeveloperMode(progress: LearnerProgress, enabled: Boolean): LearnerProgress = when {
        enabled && progress.developerMode -> progress.copy(coins = Long.MAX_VALUE)
        enabled -> progress.copy(
            developerMode = true,
            coinsBeforeDeveloperMode = progress.coins,
            coins = Long.MAX_VALUE,
            ownedAvatarPartIds = AvatarPartCatalog.all.map { it.id }.distinct(),
        )
        !progress.developerMode -> progress
        else -> progress.copy(
            developerMode = false,
            coins = progress.coinsBeforeDeveloperMode ?: 0L,
            coinsBeforeDeveloperMode = null,
        )
    }

    fun hasCompleted(progress: LearnerProgress, courseId: String, lessonId: String): Boolean =
        "$courseId:$lessonId" in progress.completedLessonIds

    /** Award any newly satisfied milestones after restoring a saved profile. */
    fun claimAvailableRewards(progress: LearnerProgress): ProgressionMutation = awardNewRewards(progress)

    fun dailyQuests(progress: LearnerProgress): List<DailyQuestProgress> {
        val selectedIds = progress.daily.selectedQuestIds.ifEmpty { selectDailyQuestIds(progress.daily.date) }
        return selectedIds.mapNotNull { selectedId -> questDefinitions.firstOrNull { it.id == selectedId } }.map { quest ->
            questProgress(
                id = quest.id,
                title = quest.title,
                description = quest.description,
                current = quest.current(progress.daily),
                target = quest.target,
                rewardCoins = quest.rewardCoins,
                claimedIds = progress.daily.claimedQuestIds,
            )
        }
    }

    fun weeklyQuests(progress: LearnerProgress): List<DailyQuestProgress> = weeklyQuestDefinitions.map { quest ->
        questProgress(
            id = quest.id,
            title = quest.title,
            description = quest.description,
            current = quest.current(progress.weekly),
            target = quest.target,
            rewardCoins = quest.rewardCoins,
            claimedIds = progress.weekly.claimedQuestIds,
        )
    }

    fun achievements(progress: LearnerProgress): List<AchievementProgress> = achievementDefinitions.map { achievement ->
        AchievementProgress(
            id = achievement.id,
            title = achievement.title,
            description = achievement.description,
            icon = achievement.icon,
            current = achievement.current(progress).coerceAtLeast(0),
            target = achievement.target,
            rewardCoins = achievement.rewardCoins,
            isUnlocked = achievement.id in progress.unlockedAchievementIds,
        )
    }

    /** Converts v2.1 avatar IDs and 0..100 sliders into the current 0..1 model. */
    fun migrateLegacyAppearance(progress: LearnerProgress): LearnerProgress {
        val appearance = progress.characterAppearance
        val legacyIds = listOf(
            appearance.skinToneId,
            appearance.hairStyleId,
            appearance.hairColorId,
            appearance.eyeStyleId,
            appearance.eyeColorId,
            appearance.noseStyleId,
        )
        val legacyShapeValues = listOf(
            appearance.hairSize,
            appearance.hairHeight,
            appearance.eyeSize,
            appearance.eyeSpacing,
            appearance.eyeHeight,
            appearance.noseSize,
            appearance.noseHeight,
        )
        val hasLegacyFormat = progress.ownedAppearanceItemIds.isNotEmpty() ||
            legacyIds.any { it in legacyAppearancePartIds } ||
            legacyShapeValues.any { it > 1f }
        if (!hasLegacyFormat) return progress

        fun mapped(id: String): String = legacyAppearancePartIds[id] ?: id

        val ownedIds = (progress.ownedAvatarPartIds + progress.ownedAppearanceItemIds.map(::mapped))
            .filter { AvatarPartCatalog.find(it) != null }
            .distinct()
        val migratedAppearance = appearance.copy(
            skinToneId = mapped(appearance.skinToneId),
            hairStyleId = mapped(appearance.hairStyleId),
            hairColorId = mapped(appearance.hairColorId),
            eyeStyleId = mapped(appearance.eyeStyleId),
            eyeColorId = mapped(appearance.eyeColorId),
            noseStyleId = mapped(appearance.noseStyleId),
            hairSize = legacySlider(appearance.hairSize),
            hairHeight = legacySlider(appearance.hairHeight),
            eyeSize = legacySlider(appearance.eyeSize),
            eyeSpacing = legacySlider(appearance.eyeSpacing),
            eyeHeight = legacySlider(appearance.eyeHeight),
            noseSize = legacySlider(appearance.noseSize),
            noseHeight = legacySlider(appearance.noseHeight),
        )
        val migrated = progress.copy(
            ownedAppearanceItemIds = emptyList(),
            ownedAvatarPartIds = (ownedIds + AvatarPartCatalog.startingOwnedIds).distinct(),
            characterAppearance = migratedAppearance,
        )
        return setCharacterAppearance(migrated, migratedAppearance)
    }

    /** Unlock one avatar part with earned coins; purchases never alter study mechanics. */
    fun unlockAvatarPart(progress: LearnerProgress, partId: String): ProgressionMutation {
        val part = AvatarPartCatalog.find(partId) ?: return ProgressionMutation(progress)
        if (part.id in progress.ownedAvatarPartIds) return ProgressionMutation(progress)
        if (progress.developerMode) {
            return ProgressionMutation(
                progress.copy(
                    coins = Long.MAX_VALUE,
                    ownedAvatarPartIds = (progress.ownedAvatarPartIds + part.id).distinct(),
                ),
            )
        }
        if (progress.coins < part.costCoins) return ProgressionMutation(progress)
        return ProgressionMutation(
            progress.copy(
                coins = progress.coins - part.costCoins,
                ownedAvatarPartIds = (progress.ownedAvatarPartIds + part.id).distinct(),
            ),
            coinsSpent = part.costCoins,
        )
    }

    /** Only owned, correctly categorized pieces can be equipped; sizing remains free. */
    fun setCharacterAppearance(progress: LearnerProgress, appearance: CharacterAppearance): LearnerProgress {
        val current = progress.characterAppearance
        val owned = progress.ownedAvatarPartIds.toSet()
        fun ownedSelection(id: String, category: AvatarPartCategory, fallback: String): String =
            id.takeIf { it in owned && AvatarPartCatalog.find(it)?.category == category } ?: fallback

        return progress.copy(
            characterAppearance = appearance.copy(
                hairStyleId = ownedSelection(appearance.hairStyleId, AvatarPartCategory.HAIR_STYLE, current.hairStyleId),
                eyeStyleId = ownedSelection(appearance.eyeStyleId, AvatarPartCategory.EYE_STYLE, current.eyeStyleId),
                noseStyleId = ownedSelection(appearance.noseStyleId, AvatarPartCategory.NOSE_STYLE, current.noseStyleId),
                skinToneId = ownedSelection(appearance.skinToneId, AvatarPartCategory.SKIN_TONE, current.skinToneId),
                faceWidth = appearance.faceWidth.coerceIn(0f, 1f),
                faceHeight = appearance.faceHeight.coerceIn(0f, 1f),
                faceRoundness = appearance.faceRoundness.coerceIn(0f, 1f),
                hairColorId = ownedSelection(appearance.hairColorId, AvatarPartCategory.HAIR_COLOR, current.hairColorId),
                eyeColorId = ownedSelection(appearance.eyeColorId, AvatarPartCategory.EYE_COLOR, current.eyeColorId),
                eyeSpacing = appearance.eyeSpacing.coerceIn(0f, 1f),
                eyeSize = appearance.eyeSize.coerceIn(0f, 1f),
                eyeHeight = appearance.eyeHeight.coerceIn(0f, 1f),
                noseSize = appearance.noseSize.coerceIn(0f, 1f),
                noseHeight = appearance.noseHeight.coerceIn(0f, 1f),
                hairSize = appearance.hairSize.coerceIn(0f, 1f),
                hairHeight = appearance.hairHeight.coerceIn(0f, 1f),
                hairVolume = appearance.hairVolume.coerceIn(0f, 1f),
            ),
        )
    }

    fun streakRecoveryCost(missedDays: Int): Long {
        if (missedDays <= 0) return BASE_STREAK_RECOVERY_COST
        var cost = BASE_STREAK_RECOVERY_COST
        repeat((missedDays - 1).coerceAtMost(MAX_COST_DOUBLINGS)) {
            cost = (cost * 2).coerceAtMost(MAX_STREAK_RECOVERY_COST)
        }
        return cost
    }

    fun buyBackStreak(progress: LearnerProgress, today: LocalDate): ProgressionMutation {
        val current = forToday(progress, today)
        val recovery = current.streakRecovery ?: return ProgressionMutation(current)
        val cost = streakRecoveryCost(recovery.missedDays)
        if (current.coins < cost) return ProgressionMutation(current)
        return ProgressionMutation(
            current.copy(
                coins = if (current.developerMode) Long.MAX_VALUE else current.coins - cost,
                streakDays = maxOf(current.streakDays, recovery.streakDays),
                bestStreakDays = maxOf(current.bestStreakDays, recovery.streakDays),
                lastStudyDate = today.toString(),
                streakRecovery = null,
            ),
        )
    }

    private fun recordStudyDay(progress: LearnerProgress, today: LocalDate): LearnerProgress {
        val date = today.toString()
        val alreadyCountedForWeek = date in progress.weekly.studyDates
        val weekly = if (alreadyCountedForWeek) {
            progress.weekly
        } else {
            progress.weekly.copy(studyDates = progress.weekly.studyDates + date)
        }
        val totalStudyDays = progress.totalStudyDays + if (alreadyCountedForWeek) 0 else 1
        val alreadyCountedToday = progress.lastStudyDate == date
        if (alreadyCountedToday) return progress.copy(weekly = weekly, totalStudyDays = totalStudyDays)

        val elapsed = progress.lastStudyDate?.let { daysBetween(it, today) }
        val nextStreak = when {
            progress.streakDays <= 0 -> 1
            elapsed == 1L -> progress.streakDays + 1
            else -> 1
        }
        var recovery = progress.streakRecovery
        if (elapsed != null && elapsed > 1 && recovery == null && progress.streakDays > 0) {
            recovery = StreakRecovery(
                streakDays = progress.streakDays,
                lastStudyDate = progress.lastStudyDate.orEmpty(),
                missedDays = (elapsed - 1).toInt(),
            )
        }
        return progress.copy(
            lastStudyDate = date,
            streakDays = nextStreak,
            bestStreakDays = maxOf(progress.bestStreakDays, nextStreak),
            totalStudyDays = totalStudyDays,
            streakRecovery = recovery,
            weekly = weekly,
        )
    }

    private fun awardNewRewards(progress: LearnerProgress): ProgressionMutation {
        val selectedIds = progress.daily.selectedQuestIds.ifEmpty { selectDailyQuestIds(progress.daily.date) }
        val newlyEarnedDaily = questDefinitions.filter { quest ->
            quest.id in selectedIds &&
                quest.current(progress.daily) >= quest.target &&
                quest.id !in progress.daily.claimedQuestIds
        }
        val newlyEarnedWeekly = weeklyQuestDefinitions.filter { quest ->
            quest.current(progress.weekly) >= quest.target && quest.id !in progress.weekly.claimedQuestIds
        }
        val dailyCoins = newlyEarnedDaily.sumOf { it.rewardCoins }
        val weeklyCoins = newlyEarnedWeekly.sumOf { it.rewardCoins }
        val withQuestRewards = progress.copy(
            coins = if (progress.developerMode) Long.MAX_VALUE else saturatingCoinsAdd(progress.coins, dailyCoins + weeklyCoins),
            daily = progress.daily.copy(
                claimedQuestIds = (progress.daily.claimedQuestIds + newlyEarnedDaily.map { it.id }).distinct(),
            ),
            weekly = progress.weekly.copy(
                claimedQuestIds = (progress.weekly.claimedQuestIds + newlyEarnedWeekly.map { it.id }).distinct(),
            ),
        )
        val newlyUnlocked = achievementDefinitions.filter { achievement ->
            achievement.current(withQuestRewards) >= achievement.target &&
                achievement.id !in withQuestRewards.unlockedAchievementIds
        }
        val achievementCoins = newlyUnlocked.sumOf { it.rewardCoins }
        val updated = withQuestRewards.copy(
            coins = if (withQuestRewards.developerMode) {
                Long.MAX_VALUE
            } else {
                saturatingCoinsAdd(withQuestRewards.coins, achievementCoins)
            },
            unlockedAchievementIds = (withQuestRewards.unlockedAchievementIds + newlyUnlocked.map { it.id }).distinct(),
        )
        return ProgressionMutation(updated, dailyCoins + weeklyCoins + achievementCoins)
    }

    private fun selectDailyQuestIds(date: String): List<String> =
        questDefinitions.shuffled(Random(date.hashCode() xor DAILY_QUEST_SEED)).take(DAILY_QUEST_COUNT).map { it.id }

    private fun saturatingCoinsAdd(balance: Long, reward: Long): Long =
        if (reward > 0L && balance > Long.MAX_VALUE - reward) Long.MAX_VALUE else balance + reward

    private fun questProgress(
        id: String,
        title: String,
        description: String,
        current: Int,
        target: Int,
        rewardCoins: Long,
        claimedIds: List<String>,
    ) = DailyQuestProgress(
        id = id,
        title = title,
        description = description,
        current = current,
        target = target,
        rewardCoins = rewardCoins,
        isClaimed = id in claimedIds,
    )

    private fun weekId(date: LocalDate): String =
        (date.toEpochDays() - date.dayOfWeek.ordinal.toLong()).toString()

    private fun daysBetween(from: String, to: LocalDate): Long? {
        val start = runCatching { LocalDate.parse(from) }.getOrNull() ?: return null
        val difference = to.toEpochDays() - start.toEpochDays()
        return difference.takeIf { it >= 0 }
    }

    private fun legacySlider(value: Float): Float = (value / 100f).coerceIn(0f, 1f)

    private val legacyAppearancePartIds = mapOf(
        "skin-deep" to "skin-brown",
        "skin-dark" to "skin-deep",
        "hair-short" to "hair-crop",
        "hair-wavy" to "hair-waves",
        "hair-colour-brown" to "hair-dark-brown",
        "hair-colour-black" to "hair-black",
        "hair-colour-blonde" to "hair-blonde",
        "hair-colour-copper" to "hair-auburn",
        "hair-colour-silver" to "hair-silver",
        "hair-colour-rose" to "hair-rose",
        "hair-colour-blue" to "hair-blue",
        "eyes-classic" to "eyes-round",
        "eye-colour-brown" to "eyes-brown",
        "eye-colour-blue" to "eyes-blue",
        "eye-colour-green" to "eyes-green",
        "eye-colour-hazel" to "eyes-hazel",
        "eye-colour-grey" to "eyes-grey",
        "eye-colour-violet" to "eyes-violet",
        "nose-classic" to "nose-button",
        "nose-wide" to "nose-soft",
        "nose-straight" to "nose-bridge",
    )

    private const val MAX_DISPLAY_NAME_LENGTH = 24
    private const val DAILY_QUEST_COUNT = 3
    private const val DAILY_QUEST_SEED = 0x51A7C3
    private const val BASE_STREAK_RECOVERY_COST = 25L
    private const val MAX_STREAK_RECOVERY_COST = 1_000_000L
    private const val MAX_COST_DOUBLINGS = 16
}
