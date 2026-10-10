package com.revisionapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.CharacterAvatar
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip

/** Store tab for earning individual character parts with progression coins. */
@Composable
fun CosmeticsStoreContent(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val ready = state.progressionReady.collectAsState().value
    val category by state.cosmeticsStoreCategory.collectAsState()
    val parts = AvatarPartCatalog.inCategory(category)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Row(
                Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CharacterAvatar(progress.characterAppearance, size = 70.dp, animated = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SectionLabel("COSMETICS WALLET")
                    Text("${progress.coins} coins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Earned through lessons and practice", style = MaterialTheme.typography.bodySmall)
                }
                Text("✦", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("BUILD YOUR LOOK")
            Text(
                "Unlock hair, eyes, noses and colour palettes. Natural skin tones are free for everyone; styling sliders are always free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in AvatarPartCategory.entries) {
                ToggleChip(
                    label = "${option.icon} ${option.title}",
                    selected = option == category,
                    onClick = { state.selectCosmeticsCategory(option) },
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(category.icon, style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text(category.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${parts.count { it.id in progress.ownedAvatarPartIds }} of ${parts.size} unlocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (category == AvatarPartCategory.SKIN_TONE) {
                    Text("8 natural tones free", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        for (part in parts) {
            CosmeticPartCard(
                part = part,
                owned = part.id in progress.ownedAvatarPartIds,
                equipped = isEquipped(part, progress.characterAppearance),
                canAfford = progress.coins >= part.costCoins,
                ready = ready,
                onUnlock = { state.unlockAvatarPart(part.id) },
            )
        }

        OutlinedButton(
            onClick = { state.navigate(Route.CharacterCustomizer) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Try your unlocked styles in Profile") }
    }
}

@Composable
private fun CosmeticPartCard(
    part: AvatarPart,
    owned: Boolean,
    equipped: Boolean,
    canAfford: Boolean,
    ready: Boolean,
    onUnlock: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = if (equipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PartPreview(part)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(part.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(part.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (part.costCoins == 0L) "FREE" else "🪙 ${part.costCoins}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            when {
                equipped -> OutlinedButton(onClick = {}, enabled = false) { Text("Equipped") }
                owned -> OutlinedButton(onClick = {}, enabled = false) { Text("Owned") }
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
            Text("●", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
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

private fun isEquipped(part: AvatarPart, appearance: CharacterAppearance): Boolean =
    when (part.category) {
        AvatarPartCategory.HAIR_STYLE -> part.id == appearance.hairStyleId
        AvatarPartCategory.EYE_STYLE -> part.id == appearance.eyeStyleId
        AvatarPartCategory.NOSE_STYLE -> part.id == appearance.noseStyleId
        AvatarPartCategory.SKIN_TONE -> part.id == appearance.skinToneId
        AvatarPartCategory.HAIR_COLOR -> part.id == appearance.hairColorId
        AvatarPartCategory.EYE_COLOR -> part.id == appearance.eyeColorId
    }
