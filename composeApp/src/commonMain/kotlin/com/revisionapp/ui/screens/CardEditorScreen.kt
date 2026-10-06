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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.model.AnswerType
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.ContentSource
import com.revisionapp.domain.model.KeyPoint
import com.revisionapp.domain.model.Mcq
import com.revisionapp.domain.model.NumericSpec
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.usecase.LibrarySnapshot
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.LabeledField
import com.revisionapp.ui.components.MathText
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip

/** One editable key point. Synonyms are typed as a comma-separated list. */
private data class KeyPointDraft(
    val text: String,
    val synonyms: String,
    val mustInclude: Boolean,
    val weight: String,
) {
    fun toKeyPoint(): KeyPoint = KeyPoint(
        text = text.trim(),
        synonyms = splitList(synonyms),
        mustInclude = mustInclude,
        weight = weight.trim().toDoubleOrNull() ?: 1.0,
    )
}

/** Everything the card form edits, as plain strings so text fields stay dumb. */
private data class CardDraft(
    val topicId: TopicId?,
    val front: String,
    val back: String,
    val answerType: AnswerType,
    val keyPoints: List<KeyPointDraft>,
    val aliases: String,
    val tileChunks: String,
    val mcqCorrect: String,
    val mcqDistractors: String,
    val explanation: String,
    val numericValue: String,
    val numericTolerance: String,
    val numericUnit: String,
    val specRef: String,
    val tagIds: Set<TagId>,
) {
    val canSave: Boolean get() = topicId != null && front.isNotBlank() && back.isNotBlank()

    fun toCard(id: CardId, source: ContentSource): Card = Card(
        id = id,
        topicId = topicId ?: TopicId(""),
        tagIds = tagIds,
        front = front.trim(),
        back = back.trim(),
        answerType = answerType,
        keyPoints = keyPoints.map { it.toKeyPoint() }.filter { it.text.isNotEmpty() },
        acceptedAliases = splitList(aliases),
        tileAnswer = splitList(tileChunks).takeIf { it.isNotEmpty() },
        mcq = toMcq(),
        explanation = explanation.trim().takeIf { it.isNotEmpty() },
        numeric = toNumericSpec(),
        source = source,
        specRef = specRef.trim(),
    )

    /** Only authored when there is a correct answer and three distinct distractors. */
    private fun toMcq(): Mcq? {
        val distractors = splitList(mcqDistractors)
        val correct = mcqCorrect.trim().ifBlank { back.trim() }
        if (correct.isBlank() || distractors.size < Mcq.MIN_DISTRACTORS) return null
        return Mcq(correct = correct, distractors = distractors.take(Mcq.MIN_DISTRACTORS))
    }

    private fun toNumericSpec(): NumericSpec? {
        val value = numericValue.trim().toDoubleOrNull()
        val tolerance = numericTolerance.trim().toDoubleOrNull()
        val unit = numericUnit.trim().takeIf { it.isNotEmpty() }
        if (value == null && tolerance == null && unit == null) return null
        return NumericSpec(value = value, tolerance = tolerance, unit = unit)
    }

    companion object {
        fun from(card: Card): CardDraft = CardDraft(
            topicId = card.topicId,
            front = card.front,
            back = card.back,
            answerType = card.answerType,
            keyPoints = card.keyPoints.map {
                KeyPointDraft(it.text, it.synonyms.joinToString(", "), it.mustInclude, it.weight.toString())
            },
            aliases = card.acceptedAliases.joinToString(", "),
            tileChunks = card.tileAnswer.orEmpty().joinToString("\n"),
            mcqCorrect = card.mcq?.correct.orEmpty(),
            mcqDistractors = card.mcq?.distractors.orEmpty().joinToString("\n"),
            explanation = card.explanation.orEmpty(),
            numericValue = card.numeric?.value?.toString().orEmpty(),
            numericTolerance = card.numeric?.tolerance?.toString().orEmpty(),
            numericUnit = card.numeric?.unit.orEmpty(),
            specRef = card.specRef,
            tagIds = card.tagIds,
        )

        fun newCard(topicId: TopicId?): CardDraft = CardDraft(
            topicId = topicId,
            front = "",
            back = "",
            answerType = AnswerType.TEXT,
            keyPoints = emptyList(),
            aliases = "",
            tileChunks = "",
            mcqCorrect = "",
            mcqDistractors = "",
            explanation = "",
            numericValue = "",
            numericTolerance = "",
            numericUnit = "",
            specRef = "",
            tagIds = emptySet(),
        )
    }
}

/** Splits on commas and newlines, trims, and drops blanks. */
private fun splitList(raw: String): List<String> =
    raw.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }

private fun List<KeyPointDraft>.replaceAt(index: Int, value: KeyPointDraft): List<KeyPointDraft> =
    mapIndexed { position, item -> if (position == index) value else item }

/**
 * Creates or edits one card. Built-in cards open read-only, because the brief
 * keeps downloaded content immutable; the "Duplicate as mine" button is the
 * supported way to start from one.
 */
@Composable
fun CardEditorScreen(state: AppState, cardId: CardId?, presetTopicId: TopicId?) {
    val snapshot = state.snapshot.collectAsState().value
    val existing = cardId?.let { snapshot.cardById(it) }
    val builtIn = existing?.source == ContentSource.BUILTIN
    // `remember` is called unconditionally so the slot order never depends on the route.
    val generatedId = remember(cardId) { state.newCardId() }
    val id = cardId ?: generatedId

    val draft = remember(cardId, existing) {
        val fresh = CardDraft.newCard(presetTopicId ?: snapshot.topics.firstOrNull()?.id)
        mutableStateOf(existing?.let { CardDraft.from(it) } ?: fresh)
    }
    val deleteAction: (() -> Unit)? = if (existing == null) {
        null
    } else {
        {
            state.deleteCard(id)
            state.back()
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = editorTitle(existing == null, builtIn, "card"),
            subtitle = if (builtIn) "Read-only - duplicate it to make your own editable copy" else id.value,
            onBack = { state.back() },
            trailing = {
                if (builtIn && existing != null) {
                    TextButton(onClick = { state.duplicateCard(existing) }) { Text("Duplicate as mine") }
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopicPicker(state, snapshot, draft.value.topicId, builtIn) { chosen ->
                draft.value = draft.value.copy(topicId = chosen)
            }
            LabeledField(
                label = "Front (the question)",
                value = draft.value.front,
                onValueChange = { draft.value = draft.value.copy(front = it) },
                enabled = !builtIn,
                minLines = 2,
                hint = "What is the SI unit of force?",
            )
            LabeledField(
                label = "Back (the model answer)",
                value = draft.value.back,
                onValueChange = { draft.value = draft.value.copy(back = it) },
                enabled = !builtIn,
                minLines = 2,
                hint = "The newton, N",
            )

            HorizontalDivider()
            SectionLabel("Preview")
            MathText(
                draft.value.front.ifBlank { "The question as the student will see it" },
                style = MaterialTheme.typography.titleMedium,
            )
            MathText(
                draft.value.back.ifBlank { "The model answer as the student will see it" },
                style = MaterialTheme.typography.bodyLarge,
            )

            SectionLabel("Answer type")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (type in AnswerType.entries) {
                    ToggleChip(
                        label = type.name,
                        selected = draft.value.answerType == type,
                        onClick = { draft.value = draft.value.copy(answerType = type) },
                        enabled = !builtIn,
                    )
                }
            }
            EmptyMessage(answerTypeHint(draft.value.answerType))

            if (draft.value.answerType == AnswerType.NUMERIC) {
                NumericFields(draft.value, builtIn) { updated -> draft.value = updated }
            }

            HorizontalDivider()
            KeyPointEditor(draft.value, builtIn) { updated -> draft.value = updated }

            HorizontalDivider()
            LabeledField(
                label = "Accepted aliases (comma separated)",
                value = draft.value.aliases,
                onValueChange = { draft.value = draft.value.copy(aliases = it) },
                enabled = !builtIn,
                hint = "newton, N, kg m/s^2",
            )
            LabeledField(
                label = "Tile answer chunks (one per line, optional)",
                value = draft.value.tileChunks,
                onValueChange = { draft.value = draft.value.copy(tileChunks = it) },
                enabled = !builtIn,
                minLines = 2,
                hint = "Leave blank to split the model answer into words automatically",
            )
            McqFields(draft.value, builtIn) { updated -> draft.value = updated }
            LabeledField(
                label = "Explanation (optional)",
                value = draft.value.explanation,
                onValueChange = { draft.value = draft.value.copy(explanation = it) },
                enabled = !builtIn,
                minLines = 2,
            )
            LabeledField(
                label = "Specification reference (optional)",
                value = draft.value.specRef,
                onValueChange = { draft.value = draft.value.copy(specRef = it) },
                enabled = !builtIn,
                hint = "H556 5.1.2 - Newton's laws",
            )

            HorizontalDivider()
            TagPicker(snapshot, draft.value.tagIds, builtIn) { tags ->
                draft.value = draft.value.copy(tagIds = tags)
            }

            if (!builtIn) {
                SaveBar(
                    canSave = draft.value.canSave,
                    onSave = {
                        val source = existing?.source ?: ContentSource.USER
                        state.saveCard(draft.value.toCard(id, source))
                        state.back()
                    },
                    onDelete = deleteAction,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "New card" / "Built-in card" / "Edit card", and the same for topics and tags. */
private fun editorTitle(isNew: Boolean, isBuiltIn: Boolean, noun: String): String = when {
    isNew -> "New " + noun
    isBuiltIn -> "Built-in " + noun
    else -> "Edit " + noun
}

private fun answerTypeHint(type: AnswerType): String = when (type) {
    AnswerType.TEXT -> "Graded by key points, aliases and fuzzy matching."
    AnswerType.NUMERIC -> "Compared with a tolerance; fill in the expected value below or leave it to be read from the model answer."
    AnswerType.EXPRESSION -> "Compared structurally after canonicalisation; falls back to self-grading when that is undecidable."
    AnswerType.SELF_GRADE -> "The model answer is shown and you judge it yourself. Best for proofs and worked reasoning."
}

@Composable
private fun TopicPicker(
    state: AppState,
    snapshot: LibrarySnapshot,
    selected: TopicId?,
    enabled: Boolean,
    onChoose: (TopicId) -> Unit,
) {
    val open = remember { mutableStateOf(false) }
    val name = snapshot.topics.firstOrNull { it.id == selected }?.name ?: "Choose a topic"
    Column {
        SectionLabel("Topic")
        Box {
            OutlinedButton(onClick = { open.value = true }, enabled = enabled) {
                Text(name, modifier = Modifier.fillMaxWidth())
            }
            DropdownMenu(expanded = open.value, onDismissRequest = { open.value = false }) {
                for (node in snapshot.tree.flatten()) {
                    DropdownMenuItem(
                        text = { Text(INDENT.repeat(node.depth) + node.name) },
                        onClick = {
                            onChoose(node.id)
                            open.value = false
                        },
                    )
                }
                if (snapshot.topics.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No topics yet") },
                        onClick = {
                            open.value = false
                            state.navigate(Route.EditTopic(null))
                        },
                    )
                }
            }
        }
    }
}

private const val INDENT = "    "

@Composable
private fun NumericFields(draft: CardDraft, enabled: Boolean, onUpdate: (CardDraft) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            LabeledField(
                label = "Expected value",
                value = draft.numericValue,
                onValueChange = { onUpdate(draft.copy(numericValue = it)) },
                enabled = enabled,
                hint = "9.81",
            )
        }
        Box(Modifier.weight(1f)) {
            LabeledField(
                label = "Tolerance",
                value = draft.numericTolerance,
                onValueChange = { onUpdate(draft.copy(numericTolerance = it)) },
                enabled = enabled,
                hint = "0.02",
            )
        }
        Box(Modifier.weight(1f)) {
            LabeledField(
                label = "Unit",
                value = draft.numericUnit,
                onValueChange = { onUpdate(draft.copy(numericUnit = it)) },
                enabled = enabled,
                hint = "m/s^2",
            )
        }
    }
}

@Composable
private fun KeyPointEditor(draft: CardDraft, enabled: Boolean, onUpdate: (CardDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Key points (" + draft.keyPoints.size + ")")
        EmptyMessage(
            "Each point must appear in the answer for it to count. Points marked \"must include\" are " +
                "required for a Correct verdict; the others are weighted and add up.",
        )
        for ((index, point) in draft.keyPoints.withIndex()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = point.text,
                    onValueChange = { text -> onUpdate(draft.copy(keyPoints = draft.keyPoints.replaceAt(index, point.copy(text = text)))) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Key point") },
                )
                OutlinedTextField(
                    value = point.synonyms,
                    onValueChange = { text -> onUpdate(draft.copy(keyPoints = draft.keyPoints.replaceAt(index, point.copy(synonyms = text)))) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Synonyms, comma separated") },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked = point.mustInclude,
                        onCheckedChange = { checked ->
                            onUpdate(draft.copy(keyPoints = draft.keyPoints.replaceAt(index, point.copy(mustInclude = checked))))
                        },
                        enabled = enabled,
                    )
                    Text("Must include", style = MaterialTheme.typography.bodyMedium)
                    Text("Weight", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = point.weight,
                        onValueChange = { text -> onUpdate(draft.copy(keyPoints = draft.keyPoints.replaceAt(index, point.copy(weight = text)))) },
                        enabled = enabled,
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                    )
                    if (enabled) {
                        TextButton(onClick = { onUpdate(draft.copy(keyPoints = draft.keyPoints.filterIndexed { position, _ -> position != index })) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
        if (enabled) {
            OutlinedButton(onClick = { onUpdate(draft.copy(keyPoints = draft.keyPoints + KeyPointDraft("", "", false, "1"))) }) {
                Text("Add key point")
            }
        }
    }
}

@Composable
private fun McqFields(draft: CardDraft, enabled: Boolean, onUpdate: (CardDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Multiple choice (optional)")
        EmptyMessage(
            "Leave blank to generate distractors from sibling cards in the same topic. Authored " +
                "distractors always win, and exactly three are needed for this card to appear in MCQ mode.",
        )
        LabeledField(
            label = "Correct option (defaults to the model answer)",
            value = draft.mcqCorrect,
            onValueChange = { onUpdate(draft.copy(mcqCorrect = it)) },
            enabled = enabled,
        )
        LabeledField(
            label = "Three distractors, one per line",
            value = draft.mcqDistractors,
            onValueChange = { onUpdate(draft.copy(mcqDistractors = it)) },
            enabled = enabled,
            minLines = 3,
        )
        val count = splitList(draft.mcqDistractors).size
        Text(
            if (count >= Mcq.MIN_DISTRACTORS) "Ready: $count distractors." else "Needs " + (Mcq.MIN_DISTRACTORS - count) + " more distractor(s).",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagPicker(
    snapshot: LibrarySnapshot,
    selected: Set<TagId>,
    enabled: Boolean,
    onChange: (Set<TagId>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("Tags")
        if (snapshot.tags.isEmpty()) {
            EmptyMessage("No tags exist yet.")
        }
        for ((groupName, tags) in snapshot.tags.groupBy { it.group.value }.toSortedMap()) {
            Text(groupName, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (tag in tags) {
                    ToggleChip(
                        label = tag.name,
                        selected = tag.id in selected,
                        onClick = {
                            onChange(if (tag.id in selected) selected - tag.id else selected + tag.id)
                        },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveBar(canSave: Boolean, onSave: () -> Unit, onDelete: (() -> Unit)?) {
    val confirmDelete = remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!canSave) {
            EmptyMessage("Pick a topic and fill in both the front and the back to save.")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f)) { Text("Save card") }
            if (onDelete != null) {
                OutlinedButton(onClick = { confirmDelete.value = true }) { Text("Delete") }
            }
        }
    }
    if (confirmDelete.value && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete.value = false },
            title = { Text("Delete this card?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete.value = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete.value = false }) { Text("Cancel") }
            },
        )
    }
}
