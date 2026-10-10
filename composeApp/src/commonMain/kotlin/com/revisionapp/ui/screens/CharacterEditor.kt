package com.revisionapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.progression.AppearanceCategory
import com.revisionapp.domain.progression.AppearanceCatalog
import com.revisionapp.domain.progression.AppearanceItem
import com.revisionapp.domain.progression.CharacterAppearance
import com.revisionapp.domain.progression.LearnerProgress
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.CharacterPortrait
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip

private enum class EditorTab(val label: String) {
    SKIN("Skin"),
    HAIR("Hair"),
    EYES("Eyes"),
    NOSE("Nose"),
}

/** Inline Profile character studio: pick unlocked pieces, then tune their shape at no cost. */
@Composable
fun CharacterEditor(state: AppState, progress: LearnerProgress, enabled: Boolean) {
    val selectedTab = remember { mutableStateOf(EditorTab.SKIN) }
    val draft = remember(progress.characterAppearance) { mutableStateOf(progress.characterAppearance) }
    val appearance = draft.value

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CharacterPortrait(appearance, width = 132.dp, height = 162.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SectionLabel("YOUR CHARACTER")
                    Text("Build your look", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Every natural skin shade is open from the start. Unlock new pieces in Store, then shape them here for free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text("🪙 ${progress.coins} coins", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorTab.entries.forEach { tab ->
                    ToggleChip(
                        label = tab.label,
                        selected = selectedTab.value == tab,
                        enabled = enabled,
                        onClick = { selectedTab.value = tab },
                    )
                }
            }

            when (selectedTab.value) {
                EditorTab.SKIN -> {
                    AppearancePicker(
                        title = "Choose a skin tone",
                        category = AppearanceCategory.SKIN_TONE,
                        selectedId = appearance.skinToneId,
                        ownedIds = progress.ownedAppearanceItemIds,
                        enabled = enabled,
                        onSelect = state::selectAppearanceItem,
                    )
                    Text(
                        "Natural tones from porcelain to deep are all unlocked. Fantasy colours are in Store → Cosmetics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                EditorTab.HAIR -> {
                    AppearancePicker(
                        title = "Hair style",
                        category = AppearanceCategory.HAIR_STYLE,
                        selectedId = appearance.hairStyleId,
                        ownedIds = progress.ownedAppearanceItemIds,
                        enabled = enabled,
                        onSelect = state::selectAppearanceItem,
                    )
                    AppearancePicker(
                        title = "Hair colour",
                        category = AppearanceCategory.HAIR_COLOR,
                        selectedId = appearance.hairColorId,
                        ownedIds = progress.ownedAppearanceItemIds,
                        enabled = enabled,
                        onSelect = state::selectAppearanceItem,
                    )
                    ShapeSlider(
                        label = "Hair size",
                        value = appearance.hairSize,
                        enabled = enabled,
                        draft = draft,
                        transform = { current, value -> current.copy(hairSize = value) },
                        onValueChangeFinished = { state.updateCharacterAppearance(draft.value) },
                    )
                    ShapeSlider(
                        label = "Hairline height",
                        value = appearance.hairHeight,
                        enabled = enabled,
                        draft = draft,
                        transform = { current, value -> current.copy(hairHeight = value) },
                        onValueChangeFinished = { state.updateCharacterAppearance(draft.value) },
                    )
                }
                EditorTab.EYES -> {
                    AppearancePicker(
                        title = "Eye shape",
                        category = AppearanceCategory.EYE_STYLE,
                        selectedId = appearance.eyeStyleId,
                        ownedIds = progress.ownedAppearanceItemIds,
                        enabled = enabled,
                        onSelect = state::selectAppearanceItem,
                    )
                    AppearancePicker(
                        title = "Eye colour",
                        category = AppearanceCategory.EYE_COLOR,
                        selectedId = appearance.eyeColorId,
                        ownedIds = progress.ownedAppearanceItemIds,
                        enabled = enabled,
                        onSelect = state::selectAppearanceItem,
                    )
                    ShapeSlider(
                        label = "Eye size",
                        value = appearance.eyeSize,
                        enabled = enabled,
                        draft = draft,
                        transform = { current, value -> current.copy(eyeSize = value) },
                        onValueChangeFinished = { state.updateCharacterAppearance(draft.value) },
                    )
                    ShapeSlider(
                        label = "Eye spacing",
                        value = appearance.eyeSpacing,
                        enabled = enabled,
                        draft = draft,
                        transform = { current, value -> current.copy(eyeSpacing = value) },
                        onValueChangeFinished = { state.updateCharacterAppearance(draft.value) },
                    )
                    ShapeSlider(
                        label = "Eye height",
                        value = appearance.eyeHeight,
                        enabled = enabled,
                        draft = draft,
                        transform = { current, value -> current.copy(eyeHeight = value) },
                        onValueChangeFinished = { state.updateCharacterAppearance(draft.value) },
                    )
                }
                EditorTab.NOSE -> {
                    AppearancePicker(
                        title = "Nose style",
                        category = AppearanceCategory.NOSE_STYLE,
                        selectedId = appearance.noseStyleId,
                        ownedIds = progress.ownedAppearanceItemIds,
                        enabled = enabled,
                        onSelect = state::selectAppearanceItem,
                    )
                    ShapeSlider(
                        label = "Nose size",
                        value = appearance.noseSize,
                        enabled = enabled,
                        draft = draft,
                        transform = { current, value -> current.copy(noseSize = value) },
                        onValueChangeFinished = { state.updateCharacterAppearance(draft.value) },
                    )
                    ShapeSlider(
                        label = "Nose height",
                        value = appearance.noseHeight,
                        enabled = enabled,
                        draft = draft,
                        transform = { current, value -> current.copy(noseHeight = value) },
                        onValueChangeFinished = { state.updateCharacterAppearance(draft.value) },
                    )
                }
            }

            val lockedCount = categoriesFor(selectedTab.value).sumOf { category ->
                AppearanceCatalog.inCategory(category).count { it.id !in progress.ownedAppearanceItemIds }
            }
            if (lockedCount > 0) {
                OutlinedButton(
                    onClick = { state.openCosmeticsStore() },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Browse $lockedCount more in Cosmetics Store") }
            }
        }
    }
}

@Composable
private fun AppearancePicker(
    title: String,
    category: AppearanceCategory,
    selectedId: String,
    ownedIds: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val items = AppearanceCatalog.inCategory(category).filter { it.id in ownedIds }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { item ->
                AppearanceOption(
                    item = item,
                    selected = item.id == selectedId,
                    enabled = enabled,
                    onClick = { onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
private fun AppearanceOption(item: AppearanceItem, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(76.dp).clip(RoundedCornerShape(15.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Surface(
            modifier = Modifier
                .size(54.dp)
                .clickable(enabled = enabled, onClick = onClick),
            shape = CircleShape,
            color = item.swatchArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            if (item.swatchArgb == null) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.previewMark, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(item.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun ShapeSlider(
    label: String,
    value: Int,
    enabled: Boolean,
    draft: MutableState<CharacterAppearance>,
    transform: (CharacterAppearance, Int) -> CharacterAppearance,
    onValueChangeFinished: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text("$value%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { newValue ->
                draft.value = transform(draft.value, newValue.toInt())
            },
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            valueRange = 0f..100f,
            steps = 9,
        )
    }
}

private fun categoriesFor(tab: EditorTab): List<AppearanceCategory> = when (tab) {
    EditorTab.SKIN -> listOf(AppearanceCategory.SKIN_TONE)
    EditorTab.HAIR -> listOf(AppearanceCategory.HAIR_STYLE, AppearanceCategory.HAIR_COLOR)
    EditorTab.EYES -> listOf(AppearanceCategory.EYE_STYLE, AppearanceCategory.EYE_COLOR)
    EditorTab.NOSE -> listOf(AppearanceCategory.NOSE_STYLE)
}
