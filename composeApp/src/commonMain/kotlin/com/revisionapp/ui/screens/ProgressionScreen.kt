package com.revisionapp.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import com.revisionapp.domain.progression.AchievementProgress
import com.revisionapp.domain.progression.CharacterAppearance
import com.revisionapp.domain.progression.DailyQuestProgress
import com.revisionapp.domain.progression.LearnerProgress
import com.revisionapp.domain.progression.ProgressionRules
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.CharacterAvatar
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.theme.appCornerShape

/** Daily and weekly learning goals, earned coins, streaks and a compact badge preview. */
@Composable
fun ProgressionScreen(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val ready = state.progressionReady.collectAsState().value
    LaunchedEffect(ready) {
        if (ready) state.refreshProgressionForToday()
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(title = "Progression", subtitle = "Practice, mastery and momentum")
        if (!ready) {
            EmptyMessage("Loading your progress…", Modifier.padding(horizontal = 20.dp))
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CoinsCard(progress.coinBalanceLabel, progress.characterAppearance)
            StreakCard(progress, state)

            SectionLabel("TODAY'S QUESTS")
            Text(
                "Three fresh goals are selected each day. They reward lessons and correct recall—not just tapping through questions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (quest in ProgressionRules.dailyQuests(progress)) {
                QuestCard(quest, cadence = "TODAY")
            }

            SectionLabel("THIS WEEK")
            Text(
                "Build a steady rhythm across the week. Weekly goals reset on Monday.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (quest in ProgressionRules.weeklyQuests(progress)) {
                QuestCard(quest, cadence = "WEEK")
            }

            AchievementSummaryCard(
                achievements = ProgressionRules.achievements(progress),
                onOpen = { state.navigate(Route.Achievements) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CoinsCard(balance: String, appearance: CharacterAppearance) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = appCornerShape(22.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            CharacterAvatar(appearance, size = 68.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionLabel("YOUR LEARNING COINS")
                AnimatedContent(targetState = balance, label = "coin-balance") { shownBalance ->
                    Text("$shownBalance 🪙", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text("Spend on parts in Store · Cosmetics", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AchievementSummaryCard(achievements: List<AchievementProgress>, onOpen: () -> Unit) {
    val unlocked = achievements.count { it.isUnlocked }
    val next = achievements.firstOrNull { !it.isUnlocked }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = appCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🏅", style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionLabel("MASTERY BADGES")
                Text("$unlocked / ${achievements.size} unlocked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    next?.let { "Next up: ${it.title}" } ?: "Every badge earned—fantastic work!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Button(onClick = onOpen) { Text("View") }
        }
    }
}

@Composable
private fun StreakCard(progress: LearnerProgress, state: AppState) {
    val recovery = progress.streakRecovery
    val cost = recovery?.let { ProgressionRules.streakRecoveryCost(it.missedDays) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = appCornerShape(20.dp),
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
private fun QuestCard(quest: DailyQuestProgress, cadence: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = appCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (quest.isComplete) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (cadence == "TODAY") "☀️" else "🗓️", style = MaterialTheme.typography.titleSmall)
                }
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
private fun ProgressBar(fraction: Float) {
    val animatedFraction = animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(480),
        label = "progress-bar",
    ).value
    Box(
        Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, appCornerShape(4.dp)),
    ) {
        if (animatedFraction > 0f) {
            Box(
                Modifier.fillMaxWidth(animatedFraction).height(7.dp)
                    .background(MaterialTheme.colorScheme.primary, appCornerShape(4.dp)),
            )
        }
    }
}
