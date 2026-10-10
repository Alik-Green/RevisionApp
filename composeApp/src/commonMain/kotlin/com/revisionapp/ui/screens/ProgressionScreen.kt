package com.revisionapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.course.CourseCatalog
import com.revisionapp.domain.progression.DailyQuestProgress
import com.revisionapp.domain.progression.LearnerProgress
import com.revisionapp.domain.progression.ProgressionRules
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel

private data class Achievement(
    val title: String,
    val description: String,
    val mark: String,
    val unlocked: Boolean,
)

/** Daily quests, coins, streak recovery and milestone achievements. */
@Composable
fun ProgressionScreen(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val catalog = state.courseCatalog.collectAsState().value
    val ready = state.progressionReady.collectAsState().value
    LaunchedEffect(ready) {
        if (ready) state.refreshProgressionForToday()
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(title = "Progression", subtitle = "Streaks, quests and achievements")
        if (!ready) {
            EmptyMessage("Loading your progress…", Modifier.padding(horizontal = 20.dp))
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CoinsCard(progress.coins)
            StreakCard(progress, state)

            SectionLabel("TODAY'S QUESTS")
            Text(
                "Finish lessons and answer questions to earn coins. Rewards are added automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (quest in ProgressionRules.dailyQuests(progress)) {
                DailyQuestCard(quest)
            }

            SectionLabel("ACHIEVEMENTS")
            val achievements = achievementsFor(progress, catalog)
            Text(
                "${achievements.count { it.unlocked }} of ${achievements.size} unlocked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (achievement in achievements) {
                AchievementRow(achievement)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CoinsCard(coins: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("🪙", style = MaterialTheme.typography.headlineLarge)
            Column(Modifier.weight(1f)) {
                SectionLabel("YOUR COINS")
                Text(coins.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Text("Earned from quests", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StreakCard(progress: LearnerProgress, state: AppState) {
    val recovery = progress.streakRecovery
    val cost = recovery?.let { ProgressionRules.streakRecoveryCost(it.missedDays) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🔥", style = MaterialTheme.typography.headlineLarge)
                Column(Modifier.weight(1f)) {
                    SectionLabel("CURRENT STREAK")
                    Text(
                        "${progress.streakDays} ${if (progress.streakDays == 1) "day" else "days"}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("BEST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(progress.bestStreakDays.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            if (recovery != null && cost != null) {
                Text(
                    "You missed ${recovery.missedDays} ${if (recovery.missedDays == 1) "day" else "days"}. " +
                        "Buy back your ${recovery.streakDays}-day streak before starting over.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { state.buyBackStreak() },
                    enabled = progress.coins >= cost,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (progress.coins >= cost) "Restore streak · $cost coins" else "Need $cost coins to restore")
                }
                Text(
                    "The coin cost doubles for each additional missed day.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (progress.streakDays == 0) {
                Text("Complete a lesson today to start your streak.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Study each day to keep your streak going.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DailyQuestCard(quest: DailyQuestProgress) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(quest.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(quest.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("+${quest.rewardCoins} 🪙", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (quest.isClaimed) {
                        Text("Earned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            ProgressBar(quest.progressFraction)
            Text(
                "${quest.current.coerceAtMost(quest.target)} / ${quest.target}${if (quest.isClaimed) " · complete" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement) {
    val background = if (achievement.unlocked) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = background,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .background(
                        if (achievement.unlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(achievement.mark, style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(achievement.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (achievement.unlocked) "UNLOCKED" else "LOCKED",
                style = MaterialTheme.typography.labelSmall,
                color = if (achievement.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(7.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
    }
}

private fun achievementsFor(progress: LearnerProgress, catalog: CourseCatalog): List<Achievement> {
    val completed = progress.completedLessonIds.toSet()
    val finishedCourse = catalog.courses.any { course ->
        course.orderedLessons().isNotEmpty() && course.orderedLessons().all { "${course.id}:${it.id}" in completed }
    }
    return listOf(
        Achievement(
            title = "First steps",
            description = "Complete your first lesson",
            mark = "🌱",
            unlocked = progress.totalLessonsCompleted > 0,
        ),
        Achievement(
            title = "Curious mind",
            description = "Answer 10 questions",
            mark = "💡",
            unlocked = progress.totalQuestionsAnswered >= 10,
        ),
        Achievement(
            title = "Three in a row",
            description = "Reach a 3-day streak",
            mark = "🔥",
            unlocked = progress.bestStreakDays >= 3,
        ),
        Achievement(
            title = "Pathfinder",
            description = "Finish a complete course path",
            mark = "🏁",
            unlocked = finishedCourse,
        ),
    )
}
