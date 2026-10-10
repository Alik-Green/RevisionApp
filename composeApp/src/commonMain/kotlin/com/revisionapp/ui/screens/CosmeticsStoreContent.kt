package com.revisionapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.progression.AvatarPart
import com.revisionapp.domain.progression.AvatarPartCatalog
import com.revisionapp.domain.progression.AvatarPartCategory
import com.revisionapp.domain.progression.CharacterAppearance
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.CharacterAvatar
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip
import com.revisionapp.ui.theme.ExtendedTheme
import com.revisionapp.ui.theme.appCornerShape
import com.revisionapp.ui.theme.appHeadingWeight
import com.revisionapp.ui.theme.appInset
import com.revisionapp.ui.components.AppButton as Button

/** Cosmetics browser. The avatar and category controls stay fixed while options scroll below. */
@Composable
fun CosmeticsStoreContent(state: AppState, modifier: Modifier = Modifier) {
    val progress = state.learnerProgress.collectAsState().value
    val ready = state.progressionReady.collectAsState().value
    val storedCategory = state.cosmeticsStoreCategory.collectAsState().value
    var area by remember(storedCategory) { mutableStateOf(areaForCategory(storedCategory)) }
    var control by remember(storedCategory) { mutableStateOf(controlForCategory(storedCategory)) }
    var appearance by remember(progress.characterAppearance) { mutableStateOf(progress.characterAppearance) }

    fun save(next: CharacterAppearance) {
        appearance = next
        state.updateCharacterAppearance(next)
    }

    val selectedPartCategory = partCategoryFor(area, control)
    val parts = selectedPartCategory?.let { AvatarPartCatalog.inCategory(it) }.orEmpty()

    Column(
        modifier.fillMaxSize().padding(horizontal = appInset(16.dp), vertical = appInset(8.dp)),
        verticalArrangement = Arrangement.spacedBy(appInset(9.dp)),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = appCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
        ) {
            Row(
                Modifier.padding(horizontal = appInset(14.dp), vertical = appInset(9.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(appInset(13.dp)),
            ) {
                CharacterAvatar(appearance, size = 76.dp, animated = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appInset(3.dp))) {
                    SectionLabel("YOUR LOOK · LIVE PREVIEW")
                    Text("${progress.displayName} · ${progress.coinBalanceLabel} coins", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Choose a category, then open Style, Colour or Shape.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionLabel("CHOOSE A CATEGORY")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(appInset(8.dp)),
        ) {
            for (option in AvatarFeatureArea.entries) {
                ToggleChip(
                    label = "${option.icon} ${option.title}",
                    selected = option == area,
                    onClick = {
                        area = option
                        control = defaultControlFor(option)
                        partCategoryFor(option, control)?.let(state::selectCosmeticsCategory)
                    },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(appInset(8.dp)),
        ) {
            for (option in controlsForArea(area)) {
                ToggleChip(
                    label = option.title,
                    selected = option == control,
                    onClick = {
                        control = option
                        partCategoryFor(area, option)?.let(state::selectCosmeticsCategory)
                    },
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(appInset(11.dp)),
        ) {
            if (control == AvatarFeatureControl.SHAPE) {
                CosmeticsShapeControls(
                    area = area,
                    appearance = appearance,
                    onAppearanceChange = { appearance = it },
                    onFinished = { state.updateCharacterAppearance(it) },
                )
            } else {
                val category = selectedPartCategory
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = appCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.padding(horizontal = appInset(14.dp), vertical = appInset(11.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(appInset(8.dp)),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${area.title} · ${control.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${parts.count { it.id in progress.ownedAvatarPartIds }} of ${parts.size} unlocked · tap an owned style to equip",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (area == AvatarFeatureArea.FACE && control == AvatarFeatureControl.SKIN) {
                            Text("8 natural tones free", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (category == null || parts.isEmpty()) {
                    Text("No options are available in this category yet.", style = MaterialTheme.typography.bodyMedium)
                }
                for (part in parts) {
                    val owned = part.id in progress.ownedAvatarPartIds
                    val equipped = isEquipped(part, appearance)
                    CosmeticPartCard(
                        part = part,
                        owned = owned,
                        equipped = equipped,
                        canAfford = progress.developerMode || progress.coins >= part.costCoins,
                        ready = ready,
                        onEquip = {
                            val next = appearance.withPart(part.category, part.id)
                            save(next)
                        },
                        onUnlock = { state.unlockAvatarPart(part.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CosmeticPartCard(
    part: AvatarPart,
    owned: Boolean,
    equipped: Boolean,
    canAfford: Boolean,
    ready: Boolean,
    onEquip: () -> Unit,
    onUnlock: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = appCornerShape(17.dp),
        color = if (equipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (equipped) 2.dp else 1.dp,
            when {
                equipped && isDark -> MaterialTheme.colorScheme.tertiary
                equipped -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outlineVariant
            },
        ),
        shadowElevation = if (isDark) 2.dp else 0.dp,
    ) {
        Row(
            Modifier.padding(appInset(13.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(appInset(12.dp)),
        ) {
            PartPreview(part)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appInset(2.dp))) {
                Text(part.name, style = MaterialTheme.typography.titleSmall, fontWeight = appHeadingWeight())
                Text(part.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (part.costCoins == 0L) "FREE" else "🪙 ${part.costCoins}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDark && part.costCoins > 0L) ExtendedTheme.colors.reward else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            when {
                equipped -> Text("Equipped", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                owned -> Button(onClick = onEquip, enabled = ready) { Text("Equip") }
                else -> Button(onClick = onUnlock, enabled = ready && canAfford) {
                    Text(if (canAfford) "Unlock" else "Need ${part.costCoins}")
                }
            }
        }
    }
}

@Composable
private fun PartPreview(part: AvatarPart) {
    if (part.colorArgb != null) {
        Box(
            Modifier.size(52.dp).background(Color(part.colorArgb), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
        }
    } else {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(part.icon, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun CosmeticsShapeControls(
    area: AvatarFeatureArea,
    appearance: CharacterAppearance,
    onAppearanceChange: (CharacterAppearance) -> Unit,
    onFinished: (CharacterAppearance) -> Unit,
) {
    var draft by remember(area, appearance) { mutableStateOf(appearance) }
    val controls = when (area) {
        AvatarFeatureArea.HAIR -> listOf(
            CosmeticsShapeControl("Hair size", draft.hairSize, "Small", "Large") { draft.copy(hairSize = it) },
            CosmeticsShapeControl("Hair height", draft.hairHeight, "Higher", "Lower") { draft.copy(hairHeight = it) },
            CosmeticsShapeControl("Hair volume", draft.hairVolume, "Neat", "Full") { draft.copy(hairVolume = it) },
        )
        AvatarFeatureArea.EYES -> listOf(
            CosmeticsShapeControl("Eye spacing", draft.eyeSpacing, "Close", "Wide") { draft.copy(eyeSpacing = it) },
            CosmeticsShapeControl("Eye size", draft.eyeSize, "Small", "Large") { draft.copy(eyeSize = it) },
            CosmeticsShapeControl("Eye height", draft.eyeHeight, "Higher", "Lower") { draft.copy(eyeHeight = it) },
        )
        AvatarFeatureArea.NOSE -> listOf(
            CosmeticsShapeControl("Nose size", draft.noseSize, "Small", "Large") { draft.copy(noseSize = it) },
            CosmeticsShapeControl("Nose height", draft.noseHeight, "Higher", "Lower") { draft.copy(noseHeight = it) },
        )
        AvatarFeatureArea.FACE -> listOf(
            CosmeticsShapeControl("Face width", draft.faceWidth, "Narrow", "Wide") { draft.copy(faceWidth = it) },
            CosmeticsShapeControl("Face height", draft.faceHeight, "Short", "Long") { draft.copy(faceHeight = it) },
            CosmeticsShapeControl("Jaw roundness", draft.faceRoundness, "Tapered", "Round") { draft.copy(faceRoundness = it) },
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = appCornerShape(19.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(appInset(15.dp)), verticalArrangement = Arrangement.spacedBy(appInset(7.dp))) {
            SectionLabel("FREE ${area.title.uppercase()} SHAPE")
            Text("Fine-tune this feature any time—no coins needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (control in controls) {
                Column(verticalArrangement = Arrangement.spacedBy(appInset(2.dp))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(control.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${(control.value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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

private data class CosmeticsShapeControl(
    val label: String,
    val value: Float,
    val lowLabel: String,
    val highLabel: String,
    val update: (Float) -> CharacterAppearance,
)

private fun CharacterAppearance.withPart(category: AvatarPartCategory, partId: String): CharacterAppearance = when (category) {
    AvatarPartCategory.HAIR_STYLE -> copy(hairStyleId = partId)
    AvatarPartCategory.EYE_STYLE -> copy(eyeStyleId = partId)
    AvatarPartCategory.NOSE_STYLE -> copy(noseStyleId = partId)
    AvatarPartCategory.SKIN_TONE -> copy(skinToneId = partId)
    AvatarPartCategory.HAIR_COLOR -> copy(hairColorId = partId)
    AvatarPartCategory.EYE_COLOR -> copy(eyeColorId = partId)
}

private fun isEquipped(part: AvatarPart, appearance: CharacterAppearance): Boolean =
    when (part.category) {
        AvatarPartCategory.HAIR_STYLE -> part.id == appearance.hairStyleId
        AvatarPartCategory.EYE_STYLE -> part.id == appearance.eyeStyleId
        AvatarPartCategory.NOSE_STYLE -> part.id == appearance.noseStyleId
        AvatarPartCategory.SKIN_TONE -> part.id == appearance.skinToneId
        AvatarPartCategory.HAIR_COLOR -> part.id == appearance.hairColorId
        AvatarPartCategory.EYE_COLOR -> part.id == appearance.eyeColorId
    }
