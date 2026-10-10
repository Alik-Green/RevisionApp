package com.revisionapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.progression.AvatarPart
import com.revisionapp.domain.progression.AvatarPartCatalog
import com.revisionapp.domain.progression.AvatarPartCategory
import com.revisionapp.domain.progression.CharacterAppearance
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.CharacterAvatar
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip

/** Avatar editor lives under Profile; sizing and positioning are always free. */
@Composable
fun CharacterCustomizationScreen(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val ready = state.progressionReady.collectAsState().value
    var appearance by remember(progress.characterAppearance) { mutableStateOf(progress.characterAppearance) }
    var selectedCategory by remember { mutableStateOf(AvatarPartCategory.HAIR_STYLE) }

    fun save(next: CharacterAppearance) {
        appearance = next
        state.updateCharacterAppearance(next)
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Character designer",
            subtitle = "Make your learner look like you",
            onBack = { state.back() },
        )
        if (!ready) {
            Text("Loading your character…", modifier = Modifier.padding(20.dp))
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CharacterAvatar(appearance, size = 122.dp, celebratory = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        SectionLabel("YOUR AVATAR")
                        Text(progress.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Pick a feature, then shape it with free controls.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text("🪙 ${progress.coinBalanceLabel} earned coins", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (category in AvatarPartCategory.entries) {
                    ToggleChip(
                        label = "${category.icon} ${category.title}",
                        selected = category == selectedCategory,
                        onClick = { selectedCategory = category },
                    )
                }
            }

            val categoryParts = AvatarPartCatalog.inCategory(selectedCategory)
            val selectedPartId = appearance.partId(selectedCategory)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(19.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("${selectedCategory.icon} ${selectedCategory.title.uppercase()}")
                            Text(
                                "${categoryParts.count { it.id in progress.ownedAvatarPartIds }} unlocked · tap a tile to equip",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { state.openCosmeticsStore(selectedCategory) }) { Text("Shop") }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        for (part in categoryParts) {
                            val owned = part.id in progress.ownedAvatarPartIds
                            AvatarOptionTile(
                                part = part,
                                owned = owned,
                                selected = part.id == selectedPartId,
                                onClick = {
                                    if (owned) {
                                        save(appearance.withPart(selectedCategory, part.id))
                                    } else {
                                        state.openCosmeticsStore(selectedCategory)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            ShapeControls(
                category = selectedCategory,
                appearance = appearance,
                onAppearanceChange = { appearance = it },
                onFinished = { state.updateCharacterAppearance(it) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { save(CharacterAppearance()) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset look") }
                Button(
                    onClick = { state.openCosmeticsStore(selectedCategory) },
                    modifier = Modifier.weight(1f),
                ) { Text("Unlock more") }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun AvatarOptionTile(
    part: AvatarPart,
    owned: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier
            .width(104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, outline),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AvatarPartSwatch(part)
            Text(part.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    selected -> "EQUIPPED"
                    owned -> "OWNED"
                    else -> "🔒 ${part.costCoins}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AvatarPartSwatch(part: AvatarPart) {
    if (part.colorArgb != null) {
        Box(
            Modifier.size(34.dp).background(Color(part.colorArgb), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.labelSmall)
        }
    } else {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(part.icon, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ShapeControls(
    category: AvatarPartCategory,
    appearance: CharacterAppearance,
    onAppearanceChange: (CharacterAppearance) -> Unit,
    onFinished: (CharacterAppearance) -> Unit,
) {
    var draft by remember(category, appearance) { mutableStateOf(appearance) }
    val controls = when (category) {
        AvatarPartCategory.HAIR_STYLE -> listOf(
            ShapeControl("Hair size", draft.hairSize, "Small", "Large") { draft.copy(hairSize = it) },
            ShapeControl("Hair height", draft.hairHeight, "Higher", "Lower") { draft.copy(hairHeight = it) },
            ShapeControl("Hair volume", draft.hairVolume, "Neat", "Full") { draft.copy(hairVolume = it) },
        )
        AvatarPartCategory.EYE_STYLE, AvatarPartCategory.EYE_COLOR -> listOf(
            ShapeControl("Eye spacing", draft.eyeSpacing, "Close", "Wide") { draft.copy(eyeSpacing = it) },
            ShapeControl("Eye size", draft.eyeSize, "Small", "Large") { draft.copy(eyeSize = it) },
            ShapeControl("Eye height", draft.eyeHeight, "Higher", "Lower") { draft.copy(eyeHeight = it) },
        )
        AvatarPartCategory.NOSE_STYLE -> listOf(
            ShapeControl("Nose size", draft.noseSize, "Small", "Large") { draft.copy(noseSize = it) },
            ShapeControl("Nose height", draft.noseHeight, "Higher", "Lower") { draft.copy(noseHeight = it) },
        )
        AvatarPartCategory.SKIN_TONE -> listOf(
            ShapeControl("Face width", draft.faceWidth, "Narrow", "Wide") { draft.copy(faceWidth = it) },
            ShapeControl("Face height", draft.faceHeight, "Short", "Long") { draft.copy(faceHeight = it) },
            ShapeControl("Jaw roundness", draft.faceRoundness, "Tapered", "Round") { draft.copy(faceRoundness = it) },
        )
        AvatarPartCategory.HAIR_COLOR -> emptyList()
    }
    if (controls.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionLabel(if (category == AvatarPartCategory.SKIN_TONE) "FREE FACE SHAPE" else "FREE SHAPE CONTROLS")
            Text(
                if (category == AvatarPartCategory.SKIN_TONE) {
                    "Adjust face width, height and jaw shape anytime—no coins needed."
                } else {
                    "Fine-tune your ${category.title.lowercase()} anytime—no coins needed."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (control in controls) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(control.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${(control.value * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = control.value,
                        onValueChange = {
                            draft = control.update(it)
                            onAppearanceChange(draft)
                        },
                        onValueChangeFinished = { onFinished(draft) },
                        valueRange = 0f..1f,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(control.lowLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(control.highLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private data class ShapeControl(
    val label: String,
    val value: Float,
    val lowLabel: String,
    val highLabel: String,
    val update: (Float) -> CharacterAppearance,
)

private fun CharacterAppearance.partId(category: AvatarPartCategory): String = when (category) {
    AvatarPartCategory.HAIR_STYLE -> hairStyleId
    AvatarPartCategory.EYE_STYLE -> eyeStyleId
    AvatarPartCategory.NOSE_STYLE -> noseStyleId
    AvatarPartCategory.SKIN_TONE -> skinToneId
    AvatarPartCategory.HAIR_COLOR -> hairColorId
    AvatarPartCategory.EYE_COLOR -> eyeColorId
}

private fun CharacterAppearance.withPart(category: AvatarPartCategory, partId: String): CharacterAppearance = when (category) {
    AvatarPartCategory.HAIR_STYLE -> copy(hairStyleId = partId)
    AvatarPartCategory.EYE_STYLE -> copy(eyeStyleId = partId)
    AvatarPartCategory.NOSE_STYLE -> copy(noseStyleId = partId)
    AvatarPartCategory.SKIN_TONE -> copy(skinToneId = partId)
    AvatarPartCategory.HAIR_COLOR -> copy(hairColorId = partId)
    AvatarPartCategory.EYE_COLOR -> copy(eyeColorId = partId)
}
