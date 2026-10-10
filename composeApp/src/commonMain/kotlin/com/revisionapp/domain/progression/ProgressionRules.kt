package com.revisionapp.domain.progression

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class DailyProgress(
    val date: String = "",
    val lessonsCompleted: Int = 0,
    val questionsAnswered: Int = 0,
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
    val streakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val lastStudyDate: String? = null,
    val streakRecovery: StreakRecovery? = null,
    val completedLessonIds: List<String> = emptyList(),
    val totalLessonsCompleted: Int = 0,
    val totalQuestionsAnswered: Int = 0,
    val daily: DailyProgress = DailyProgress(),
)

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

data class ProgressionMutation(
    val progress: LearnerProgress,
    val coinsEarned: Long = 0,
)

/** Pure rules for daily quests, streaks, coins and streak recovery. */
object ProgressionRules {
    private data class QuestDefinition(
        val id: String,
        val title: String,
        val description: String,
        val target: Int,
        val rewardCoins: Long,
        val current: (DailyProgress) -> Int,
    )

    private val questDefinitions = listOf(
        QuestDefinition(
            id = "two-lessons",
            title = "Lesson streak",
            description = "Complete 2 lessons today",
            target = 2,
            rewardCoins = 30,
            current = DailyProgress::lessonsCompleted,
        ),
        QuestDefinition(
            id = "ten-questions",
            title = "Question collector",
            description = "Answer 10 questions today",
            target = 10,
            rewardCoins = 20,
            current = DailyProgress::questionsAnswered,
        ),
    )

    /** Reset today's quest counters and surface a lost streak without erasing it. */
    fun forToday(progress: LearnerProgress, today: LocalDate): LearnerProgress {
        val date = today.toString()
        val daily = if (progress.daily.date == date) progress.daily else DailyProgress(date = date)
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
        return progress.copy(daily = daily, streakDays = streak, streakRecovery = recovery)
    }

    /** Count one answered V2 question and credit a study day. */
    fun answerQuestion(progress: LearnerProgress, today: LocalDate): ProgressionMutation {
        val current = forToday(progress, today)
        val active = recordStudyDay(current, today)
        val updated = active.copy(
            totalQuestionsAnswered = active.totalQuestionsAnswered + 1,
            daily = active.daily.copy(questionsAnswered = active.daily.questionsAnswered + 1),
        )
        return awardNewQuests(updated)
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
        )
        return awardNewQuests(updated)
    }

    fun setActiveCourse(progress: LearnerProgress, courseId: String): LearnerProgress =
        progress.copy(activeCourseId = courseId)

    fun setDisplayName(progress: LearnerProgress, displayName: String): LearnerProgress =
        progress.copy(displayName = displayName.trim().take(MAX_DISPLAY_NAME_LENGTH).ifBlank { "Learner" })

    fun hasCompleted(progress: LearnerProgress, courseId: String, lessonId: String): Boolean =
        "$courseId:$lessonId" in progress.completedLessonIds

    fun dailyQuests(progress: LearnerProgress): List<DailyQuestProgress> = questDefinitions.map { quest ->
        val current = quest.current(progress.daily)
        DailyQuestProgress(
            id = quest.id,
            title = quest.title,
            description = quest.description,
            current = current,
            target = quest.target,
            rewardCoins = quest.rewardCoins,
            isClaimed = quest.id in progress.daily.claimedQuestIds,
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
                coins = current.coins - cost,
                streakDays = maxOf(current.streakDays, recovery.streakDays),
                bestStreakDays = maxOf(current.bestStreakDays, recovery.streakDays),
                lastStudyDate = today.toString(),
                streakRecovery = null,
            ),
        )
    }

    private fun recordStudyDay(progress: LearnerProgress, today: LocalDate): LearnerProgress {
        val lastDate = progress.lastStudyDate
        if (lastDate == today.toString()) return progress

        val elapsed = lastDate?.let { daysBetween(it, today) }
        val nextStreak = when {
            progress.streakDays <= 0 -> 1
            elapsed == 1L -> progress.streakDays + 1
            else -> 1
        }
        var recovery = progress.streakRecovery
        if (elapsed != null && elapsed > 1 && recovery == null && progress.streakDays > 0) {
            recovery = StreakRecovery(
                streakDays = progress.streakDays,
                lastStudyDate = lastDate.orEmpty(),
                missedDays = (elapsed - 1).toInt(),
            )
        }
        return progress.copy(
            lastStudyDate = today.toString(),
            streakDays = nextStreak,
            bestStreakDays = maxOf(progress.bestStreakDays, nextStreak),
            streakRecovery = recovery,
        )
    }

    private fun awardNewQuests(progress: LearnerProgress): ProgressionMutation {
        val newlyEarned = questDefinitions.filter { quest ->
            quest.current(progress.daily) >= quest.target && quest.id !in progress.daily.claimedQuestIds
        }
        val earned = newlyEarned.sumOf { it.rewardCoins }
        val updated = progress.copy(
            coins = progress.coins + earned,
            daily = progress.daily.copy(
                claimedQuestIds = (progress.daily.claimedQuestIds + newlyEarned.map { it.id }).distinct(),
            ),
        )
        return ProgressionMutation(updated, earned)
    }

    private fun daysBetween(from: String, to: LocalDate): Long? {
        val start = runCatching { LocalDate.parse(from) }.getOrNull() ?: return null
        val difference = to.toEpochDays() - start.toEpochDays()
        return difference.takeIf { it >= 0 }
    }

    private const val MAX_DISPLAY_NAME_LENGTH = 24
    private const val BASE_STREAK_RECOVERY_COST = 25L
    private const val MAX_STREAK_RECOVERY_COST = 1_000_000L
    private const val MAX_COST_DOUBLINGS = 16
}
