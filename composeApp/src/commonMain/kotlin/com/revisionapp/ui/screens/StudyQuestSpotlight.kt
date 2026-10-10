package com.revisionapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.progression.DailyQuestProgress
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.theme.appCornerShape

/** A single next-action card keeps the study loop visibly connected to rewards. */
@Composable
fun QuestSpotlight(quest: DailyQuestProgress?, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = appCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(
                    Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), CircleShape)
                        .padding(9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (quest == null) "🏁" else "🎯", style = MaterialTheme.typography.titleMedium) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel(if (quest == null) "DAILY BOARD" else "NEXT DAILY QUEST")
                    Text(
                        quest?.title ?: "Daily goals cleared!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        quest?.description ?: "Great work—keep the learning streak alive tomorrow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                if (quest != null) {
                    Text("+${quest.rewardCoins} 🪙", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            if (quest != null) {
                val fraction = quest.progressFraction.coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(6.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), appCornerShape(4.dp)),
                ) {
                    if (fraction > 0f) {
                        Box(
                            Modifier.fillMaxWidth(fraction).height(6.dp)
                                .background(MaterialTheme.colorScheme.tertiary, appCornerShape(4.dp)),
                        )
                    }
                }
                Text(
                    "${quest.current.coerceAtMost(quest.target)} / ${quest.target} · tap to see all goals",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
