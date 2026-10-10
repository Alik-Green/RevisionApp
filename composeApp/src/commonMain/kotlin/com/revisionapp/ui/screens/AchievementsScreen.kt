package com.revisionapp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.progression.AchievementProgress
import com.revisionapp.domain.progression.ProgressionRules
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.theme.ExtendedTheme
import com.revisionapp.ui.theme.appCornerShape
import com.revisionapp.ui.theme.appInset

/** Dedicated badge gallery so the main Progress screen can stay focused on next actions. */
@Composable
fun AchievementsScreen(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val ready = state.progressionReady.collectAsState().value
    val achievements = ProgressionRules.achievements(progress)
    val unlocked = achievements.count { it.isUnlocked }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Achievements",
            subtitle = "Mastery badges for your learning journey",
            onBack = { state.back() },
        )
        if (!ready) {
            EmptyMessage("Loading your achievements…", Modifier.padding(horizontal = appInset(20.dp)))
            return@Column
        }
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = appInset(16.dp), vertical = appInset(6.dp)),
            shape = appCornerShape(22.dp),
            color = if (isDark) ExtendedTheme.colors.rewardContainer else MaterialTheme.colorScheme.tertiaryContainer,
            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        ) {
            Row(
                Modifier.padding(appInset(16.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(appInset(14.dp)),
            ) {
                Text("🏆", style = MaterialTheme.typography.displaySmall)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appInset(2.dp))) {
                    Text("$unlocked / ${achievements.size} badges", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Lessons, confident recall, streaks and steady practice all count.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = appInset(16.dp), vertical = appInset(10.dp)),
            horizontalArrangement = Arrangement.spacedBy(appInset(12.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(12.dp)),
        ) {
            items(achievements, key = AchievementProgress::id) { achievement ->
                AchievementBadge(achievement)
            }
        }
    }
}

@Composable
private fun AchievementBadge(achievement: AchievementProgress) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = when {
        achievement.isUnlocked && isDark -> ExtendedTheme.colors.rewardContainer
        achievement.isUnlocked -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val animatedFraction = animateFloatAsState(
        targetValue = achievement.progressFraction,
        animationSpec = tween(450),
        label = "badge-progress",
    ).value
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = appCornerShape(20.dp),
        color = background,
        border = if (isDark) {
            BorderStroke(
                1.dp,
                when {
                    achievement.isUnlocked -> ExtendedTheme.colors.reward.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
            )
        } else {
            null
        },
        shadowElevation = if (isDark && achievement.isUnlocked) 3.dp else 0.dp,
        tonalElevation = if (achievement.isUnlocked) 2.dp else 0.dp,
    ) {
        Column(
            Modifier.padding(appInset(14.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(appInset(7.dp)),
        ) {
            Surface(
                shape = CircleShape,
                color = if (achievement.isUnlocked && isDark) {
                    ExtendedTheme.colors.reward
                } else if (achievement.isUnlocked) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    if (achievement.isUnlocked) achievement.icon else "🔒",
                    modifier = Modifier.padding(appInset(13.dp)),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text(
                achievement.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                achievement.description,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                if (achievement.isUnlocked) {
                    "EARNED · +${achievement.rewardCoins} 🪙"
                } else {
                    "+${achievement.rewardCoins} 🪙"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) ExtendedTheme.colors.reward else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Box(
                Modifier.fillMaxWidth().height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, appCornerShape(4.dp)),
            ) {
                if (animatedFraction > 0f) {
                    Box(
                        Modifier.fillMaxWidth(animatedFraction.coerceIn(0f, 1f)).height(6.dp)
                            .background(MaterialTheme.colorScheme.primary, appCornerShape(4.dp)),
                    )
                }
            }
            Text(
                "${achievement.current.coerceAtMost(achievement.target)} / ${achievement.target}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
