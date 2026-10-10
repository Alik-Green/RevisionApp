package com.revisionapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.progression.CharacterCosmetics
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.LearningBuddy
import com.revisionapp.ui.components.SectionLabel

/** Earned-only cosmetic shop. Character styles never change lesson or answer mechanics. */
@Composable
fun CharacterShopScreen(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val progressionReady = state.progressionReady.collectAsState().value
    val selected = CharacterCosmetics.find(progress.selectedCosmeticId) ?: CharacterCosmetics.starter

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Character studio",
            subtitle = "A little personality for your learning path",
            onBack = { state.back() },
            trailing = { Text("🪙 ${progress.coins}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LearningBuddy(selected, size = 92.dp, celebratory = true)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        SectionLabel("CURRENT COMPANION")
                        Text(selected.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Choose a look that makes your study space feel like yours.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionLabel("STYLE COLLECTION")
                Text(
                    "Coins come from lessons, correct recall, and steady practice. Styles are purely cosmetic.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            for (cosmetic in CharacterCosmetics.all) {
                val owned = cosmetic.id in progress.ownedCosmeticIds
                val equipped = cosmetic.id == progress.selectedCosmeticId
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = if (equipped) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ) {
                    Row(
                        Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LearningBuddy(cosmetic, size = 58.dp, animated = false)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(cosmetic.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                cosmetic.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (cosmetic.cost == 0L) "FREE STYLE" else "🪙 ${cosmetic.cost}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        when {
                            equipped -> OutlinedButton(onClick = {}, enabled = false) { Text("Equipped") }
                            owned -> Button(
                                onClick = { state.equipCharacterCosmetic(cosmetic.id) },
                                enabled = progressionReady,
                            ) { Text("Equip") }
                            else -> Button(
                                onClick = { state.buyCharacterCosmetic(cosmetic.id) },
                                enabled = progressionReady && progress.coins >= cosmetic.cost,
                            ) {
                                Text(if (progress.coins >= cosmetic.cost) "Unlock" else "Need ${cosmetic.cost}")
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { state.navigate(com.revisionapp.ui.Route.Progression) },
                modifier = Modifier.fillMaxWidth(),
                enabled = progressionReady,
            ) { Text("See quests and achievements") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
