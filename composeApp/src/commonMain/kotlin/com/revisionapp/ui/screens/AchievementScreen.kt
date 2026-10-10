package com.revisionapp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.progression.AchievementProgress
import com.revisionapp.domain.progression.ProgressionRules
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip

private enum class AchievementFilter(val label: String) {
    ALL("All"),
    IN_PROGRESS("In progress"),
    UNLOCKED("Unlocked"),
}

/** Focused milestone page, kept separate from the daily and weekly quest feed. */
@Composable
fun AchievementScreen(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val ready = state.progressionReady.collectAsState().value
    val achievements = ProgressionRules.achievements(progress)
    val filter = remember { mutableStateOf(AchievementFilter.ALL) }
    val visible = when (filter.value) {
        AchievementFilter.ALL -> achievements
        AchievementFilter.IN_PROGRESS -> achievements.filterNot { it.isUnlocked }
        AchievementFilter.UNLOCKED -> achievements.filter { it.isUnlocked }
    }
    val unlocked = achievements.count { it.isUnlocked }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Achievements",
            subtitle = "Milestones for learning well",
            onBack = { state.back() },
        )
        if (!ready) {
            EmptyMessage("Loading your achievements…", Modifier.padding(horizontal = 20.dp))
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    Modifier.padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("🏆", style = MaterialTheme.typography.displaySmall)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel("YOUR MASTERY BOARD")
                        Text("$unlocked / ${achievements.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Earn each reward once. Keep practising with care.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AchievementFilter.entries.forEach { option ->
                    ToggleChip(
                        label = option.label,
                        selected = filter.value == option,
                        onClick = { filter.value = option },
                    )
                }
            }
            Text(
                "${visible.size} ${if (visible.size == 1) "milestone" else "milestones"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (visible.isEmpty()) {
                EmptyMessage(
                    if (filter.value == AchievementFilter.UNLOCKED) {
                        "No milestones unlocked yet. Keep practising to earn your first one."
                    } else {
                        "All milestones are unlocked. Amazing work!"
                    },
                )
            } else {
                for (achievement in visible) AchievementCard(achievement)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementProgress) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = if (achievement.isUnlocked) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .background(
                            if (achievement.isUnlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        )
                        .padding(11.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(achievement.icon, style = MaterialTheme.typography.titleMedium) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(achievement.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (achievement.isUnlocked) "✓" else "+${achievement.rewardCoins} 🪙",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (achievement.isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            AchievementProgressBar(achievement.progressFraction)
            Text(
                if (achievement.isUnlocked) "Mastered · ${achievement.rewardCoins} coins earned"
                else "${achievement.current.coerceAtMost(achievement.target)} / ${achievement.target} · reward ${achievement.rewardCoins} coins",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AchievementProgressBar(fraction: Float) {
    val animated = animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = tween(450), label = "achievement-progress").value
    Box(
        Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    ) {
        if (animated > 0f) {
            Box(Modifier.fillMaxWidth(animated).height(7.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
        }
    }
}
