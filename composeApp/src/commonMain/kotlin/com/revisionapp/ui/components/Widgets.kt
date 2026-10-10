package com.revisionapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.ui.theme.appCornerShape
import com.revisionapp.ui.theme.appHeadingWeight
import com.revisionapp.ui.theme.appInset

/**
 * Small building blocks shared by every screen.
 *
 * Shared widgets stay small and explicit: the app uses Material icons, but keeps
 * its own `AppHeader` rather than opting into the experimental `TopAppBar` API.
 * See docs/DECISIONS.md D7 for the original navigation and shell decision.
 */

/** Primary action with a clearly raised, state-aware dark-mode profile. */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        elevation = if (isDark) {
            ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 1.dp,
                focusedElevation = 4.dp,
                hoveredElevation = 6.dp,
                disabledElevation = 0.dp,
            )
        } else {
            ButtonDefaults.buttonElevation()
        },
        content = content,
    )
}

/** Secondary outlined action with a pressed lower-edge response in dark mode. */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = MaterialTheme.shapes.small
    val depth = when {
        !isDark || !enabled -> 0.dp
        pressed -> 1.dp
        else -> 3.dp
    }
    val raisedModifier = if (depth > 0.dp) modifier.shadow(depth, shape, clip = false) else modifier
    OutlinedButton(
        onClick = onClick,
        modifier = raisedModifier,
        enabled = enabled,
        shape = shape,
        colors = if (isDark) {
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        interactionSource = interactionSource,
        content = content,
    )
}

/** Link-like secondary action; dark mode uses the requested information blue. */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = if (isDark) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
        } else {
            ButtonDefaults.textButtonColors()
        },
        content = content,
    )
}

/** Screen title bar. Hand-rolled rather than `TopAppBar`, which is experimental. */
@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = appInset(16.dp), vertical = appInset(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appInset(10.dp)),
    ) {
        val back = onBack
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        if (back != null) {
            if (isDark) {
                IconButton(onClick = back, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Button(onClick = back) { Text("Back") }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = appHeadingWeight(),
            )
            val caption = subtitle
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** Section heading, e.g. "Tags" or "Key points". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = appHeadingWeight(),
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A filter or option that can be toggled. Replaces the experimental chip APIs. */
@Composable
fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = appCornerShape(16.dp)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val depth = when {
        !isDark || !enabled -> 0.dp
        pressed -> 1.dp
        selected -> 4.dp
        else -> 2.dp
    }
    val background = when {
        selected && isDark -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.primary
        isDark -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        selected && isDark -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .then(if (depth > 0.dp) Modifier.shadow(depth, shape, clip = false) else Modifier)
            .background(background.copy(alpha = alpha), shape)
            .then(
                if (isDark) {
                    Modifier.border(
                        1.dp,
                        (if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            .copy(alpha = 0.65f),
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = interactionSource, enabled = enabled, onClick = onClick)
            .padding(horizontal = appInset(12.dp), vertical = appInset(6.dp)),
    ) {
        MathText(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground.copy(alpha = alpha),
        )
    }
}

/** Neutral explanation where a list would otherwise be blank. */
@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(vertical = appInset(12.dp)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Big number with a caption, used across the stats screen. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, appCornerShape(12.dp))
            .padding(horizontal = appInset(16.dp), vertical = appInset(12.dp)),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Label plus value on one line, for read-only detail panels. */
@Composable
fun MetaRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = appInset(3.dp))) {
        Text(
            label,
            modifier = Modifier.width(170.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A labelled multi-line-capable text field; the editor's workhorse. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String = "",
    minLines: Int = 1,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(label)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint) },
        )
    }
}
