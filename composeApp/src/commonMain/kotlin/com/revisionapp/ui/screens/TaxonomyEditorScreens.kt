package com.revisionapp.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.model.ContentSource
import com.revisionapp.domain.model.TagGroup
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.usecase.LibrarySnapshot
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppButton as Button
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.AppOutlinedButton as OutlinedButton
import com.revisionapp.ui.components.AppTextButton as TextButton
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.LabeledField
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip
import com.revisionapp.ui.theme.appInset

private const val INDENT_SPACES = "    "

/**
 * Creates, renames, reparents or deletes a user topic. Topics nest to any depth;
 * deleting one reparents its children rather than orphaning them, which
 * [AppState.deleteTopic] handles.
 */
@Composable
fun TopicEditorScreen(state: AppState, topicId: TopicId?, presetParentId: TopicId?) {
    val snapshot = state.snapshot.collectAsState().value
    val existing = topicId?.let { id -> snapshot.topics.firstOrNull { it.id == id } }
    val builtIn = existing?.source == ContentSource.BUILTIN
    val name = remember(topicId, existing) { mutableStateOf(existing?.name.orEmpty()) }
    val parentId = remember(topicId, existing, presetParentId) {
        mutableStateOf(existing?.parentId ?: presetParentId)
    }
    val confirmDelete = remember(topicId) { mutableStateOf(false) }
    val forbidden = topicId?.let { snapshot.tree.descendantsIncluding(it) } ?: emptySet()

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = when {
                existing == null -> "New topic"
                builtIn -> "Built-in topic"
                else -> "Edit topic"
            },
            subtitle = topicId?.value ?: "Topics can nest to any depth",
            onBack = { state.back() },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = appInset(16.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(12.dp)),
        ) {
            if (builtIn) {
                EmptyMessage(
                    "Built-in topics come from a content pack and are read-only, so a sync can update " +
                        "them safely. Create your own topic to build a hierarchy you control.",
                )
            }
            LabeledField(
                label = "Name",
                value = name.value,
                onValueChange = { name.value = it },
                enabled = !builtIn,
                hint = "Circular motion",
            )
            ParentPicker(snapshot, parentId.value, forbidden, !builtIn) { parentId.value = it }
            val cardCount = snapshot.cards.count { it.topicId == topicId }
            if (topicId != null) {
                Text(
                    cardCount.toString() + " card(s) in this topic right now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!builtIn) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
                    Button(
                        onClick = {
                            state.saveTopic(topicId, name.value, parentId.value)
                            state.back()
                        },
                        enabled = name.value.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (existing == null) "Create topic" else "Save topic") }
                    if (existing != null) {
                        OutlinedButton(onClick = { confirmDelete.value = true }) { Text("Delete") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete.value && topicId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete.value = false },
            title = { Text("Delete this topic?") },
            text = { Text("Its sub-topics move up one level. Cards inside it are deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete.value = false
                        state.deleteTopic(topicId)
                        state.back()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete.value = false }) { Text("Cancel") }
            },
        )
    }
}

/** Parent chooser that refuses to create a cycle by listing the topic's own subtree. */
@Composable
private fun ParentPicker(
    snapshot: LibrarySnapshot,
    selected: TopicId?,
    forbidden: Set<TopicId>,
    enabled: Boolean,
    onChoose: (TopicId?) -> Unit,
) {
    val open = remember { mutableStateOf(false) }
    val label = snapshot.topics.firstOrNull { it.id == selected }?.name ?: "None (top level)"
    Column {
        SectionLabel("Parent topic")
        Box {
            OutlinedButton(onClick = { open.value = true }, enabled = enabled) {
                Text(label, modifier = Modifier.fillMaxWidth())
            }
            DropdownMenu(expanded = open.value, onDismissRequest = { open.value = false }) {
                DropdownMenuItem(
                    text = { Text("None (top level)") },
                    onClick = {
                        onChoose(null)
                        open.value = false
                    },
                )
                for (node in snapshot.tree.flatten()) {
                    if (node.id in forbidden) continue
                    DropdownMenuItem(
                        text = { Text(INDENT_SPACES.repeat(node.depth) + node.name) },
                        onClick = {
                            onChoose(node.id)
                            open.value = false
                        },
                    )
                }
            }
        }
        EmptyMessage("A topic cannot be moved inside itself or inside one of its own descendants.")
    }
}

/** Creates, renames or deletes a user tag, including tags in a brand-new group. */
@Composable
fun TagEditorScreen(state: AppState, tagId: TagId?) {
    val snapshot = state.snapshot.collectAsState().value
    val existing = tagId?.let { id -> snapshot.tags.firstOrNull { it.id == id } }
    val builtIn = existing?.source == ContentSource.BUILTIN
    val name = remember(tagId, existing) { mutableStateOf(existing?.name.orEmpty()) }
    val group = remember(tagId, existing) { mutableStateOf(existing?.group?.value ?: TagGroup.Custom.value) }
    val confirmDelete = remember(tagId) { mutableStateOf(false) }
    val usedBy = snapshot.cards.count { tagId != null && tagId in it.tagIds }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = when {
                existing == null -> "New tag"
                builtIn -> "Built-in tag"
                else -> "Edit tag"
            },
            subtitle = tagId?.value ?: "Tags drive the filter groups",
            onBack = { state.back() },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = appInset(16.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(12.dp)),
        ) {
            if (builtIn) {
                EmptyMessage(
                    "Built-in tags come from a content pack and are read-only. They are shared across " +
                        "packs on purpose, so \"board = OCR\" means the same thing everywhere.",
                )
            }
            LabeledField(
                label = "Name",
                value = name.value,
                onValueChange = { name.value = it },
                enabled = !builtIn,
                hint = "OCR",
            )
            SectionLabel("Group")
            Row(horizontalArrangement = Arrangement.spacedBy(appInset(6.dp))) {
                for (known in TagGroup.WellKnown) {
                    ToggleChip(
                        label = known.value,
                        selected = group.value == known.value,
                        onClick = { group.value = known.value },
                        enabled = !builtIn,
                    )
                }
            }
            LabeledField(
                label = "Or type your own group",
                value = group.value,
                onValueChange = { group.value = it },
                enabled = !builtIn,
                hint = "custom",
            )
            EmptyMessage(
                "Tags in the same group are OR-ed together and different groups are AND-ed, so " +
                    "\"board\" and \"subject\" should be separate groups. $usedBy card(s) use this tag.",
            )
            if (!builtIn) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
                    Button(
                        onClick = {
                            state.saveTag(tagId, name.value, group.value)
                            state.back()
                        },
                        enabled = name.value.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (existing == null) "Create tag" else "Save tag") }
                    if (existing != null) {
                        OutlinedButton(onClick = { confirmDelete.value = true }) { Text("Delete") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete.value && tagId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete.value = false },
            title = { Text("Delete this tag?") },
            text = { Text("It is removed from every card that uses it. The cards themselves are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete.value = false
                        state.deleteTag(tagId)
                        state.back()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete.value = false }) { Text("Cancel") }
            },
        )
    }
}
