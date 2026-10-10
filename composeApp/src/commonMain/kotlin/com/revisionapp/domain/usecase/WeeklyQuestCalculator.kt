package com.revisionapp.domain.usecase

import kotlinx.datetime.LocalDate

/** One repeatable weekly goal and its current progress. */
data class WeeklyQuestProgress(
    val id: String,
    val title: String,
    val description: String,
    val targetStudyDays: Int,
    val studiedDays: Int,
    val rewardPoints: Int,
) {
    val isComplete: Boolean get() = studiedDays >= targetStudyDays
    val progressFraction: Float
        get() = if (targetStudyDays <= 0) 1f else (studiedDays.toFloat() / targetStudyDays).coerceIn(0f, 1f)
}

/** Current weekly quest progress and points earned across the full review history. */
data class WeeklyQuestSnapshot(
    val studiedDaysThisWeek: Int,
    val totalPoints: Int,
    val quests: List<WeeklyQuestProgress>,
) {
    companion object {
        val Empty = WeeklyQuestSnapshot(0, 0, emptyList())
    }
}

/**
 * Small, repeatable goals based on distinct review days rather than raw taps.
 * Rewards are derived from the append-only review history, so there is no
 * separate claim to duplicate and points never affect spaced-repetition scheduling.
 * Weeks start on Monday using ISO local dates, matching the app's streak calendar.
 */
object WeeklyQuestCalculator {

    private data class QuestDefinition(
        val id: String,
        val title: String,
        val description: String,
        val targetStudyDays: Int,
        val rewardPoints: Int,
    )

    private val quests = listOf(
        QuestDefinition(
            id = "weekly-first-step",
            title = "First step",
            description = "Study on 1 day this week",
            targetStudyDays = 1,
            rewardPoints = 5,
        ),
        QuestDefinition(
            id = "weekly-rhythm",
            title = "Build a rhythm",
            description = "Study on 3 different days this week",
            targetStudyDays = 3,
            rewardPoints = 25,
        ),
        QuestDefinition(
            id = "weekly-explorer",
            title = "Weekly explorer",
            description = "Study on 5 different days this week",
            targetStudyDays = 5,
            rewardPoints = 50,
        ),
    )

    /**
     * Review days are ISO dates (`yyyy-MM-dd`). Duplicate, malformed and future
     * values are ignored. Historical quest points are recalculated from the same
     * unique-day set, while the displayed goals only include this Monday-to-Sunday
     * week.
     */
    fun calculate(reviewDays: Collection<String>, today: LocalDate): WeeklyQuestSnapshot {
        val dates = reviewDays.mapNotNull(::parseDate).filter { it <= today }.toSet()
        val currentWeek = weekStart(today)
        val thisWeekDays = dates.filter { weekStart(it) == currentWeek }.size
        val daysByWeek = dates.groupBy(::weekStart).values
        val totalPoints = daysByWeek.sumOf { weekDays ->
            quests.filter { weekDays.size >= it.targetStudyDays }.sumOf { it.rewardPoints }
        }
        return WeeklyQuestSnapshot(
            studiedDaysThisWeek = thisWeekDays,
            totalPoints = totalPoints,
            quests = quests.map { quest ->
                WeeklyQuestProgress(
                    id = quest.id,
                    title = quest.title,
                    description = quest.description,
                    targetStudyDays = quest.targetStudyDays,
                    studiedDays = thisWeekDays,
                    rewardPoints = quest.rewardPoints,
                )
            },
        )
    }

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

    /** Epoch day of Monday for the date's ISO week. */
    private fun weekStart(date: LocalDate): Long = date.toEpochDays() - date.dayOfWeek.ordinal.toLong()
}
