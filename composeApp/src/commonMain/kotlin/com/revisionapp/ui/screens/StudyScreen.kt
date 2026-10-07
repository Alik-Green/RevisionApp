package com.revisionapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.check.Verdict
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.srs.Rating
import com.revisionapp.domain.study.McqQuestionFactory
import com.revisionapp.domain.study.Question
import com.revisionapp.domain.study.Tile
import com.revisionapp.domain.study.TileQuestionFactory
import com.revisionapp.domain.usecase.LibrarySnapshot
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.MathText
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.StatTile
import com.revisionapp.ui.components.ToggleChip
import com.revisionapp.ui.session.AnswerDraft
import com.revisionapp.ui.session.SessionCard
import com.revisionapp.ui.session.SessionEvent
import com.revisionapp.ui.session.SessionState
import com.revisionapp.ui.session.VerdictPresentation

private val CorrectGreen = Color(0xFF2E7D32)
private val PartialAmber = Color(0xFFB26A00)

/** Tiles per row; `FlowRow` is experimental, so rows are chunked by hand. */
private const val TILES_PER_ROW = 4

/**
 * The study screen. It renders [SessionState] with an exhaustive `when`, so a new
 * session state cannot be forgotten here, and delegates every input straight back
 * to [AppState.onSessionEvent] — the screen holds no logic of its own.
 */
@Composable
fun StudyScreen(state: AppState) {
    val session = state.sessionState.collectAsState().value
    when (session) {
        SessionState.Empty -> ModePicker(state)
        is SessionState.Asking -> Asking(state, session)
        is SessionState.Reviewing -> Reviewing(state, session)
        is SessionState.Finished -> Finished(state, session)
    }
}

// ------------------------------------------------------------------ picker ---

/** One mode, how many cards in scope it can actually present, and why. */
private data class ModeOption(
    val mode: StudyMode,
    val eligible: Int,
    val description: String,
    val icon: ImageVector,
) {
    val isAvailable: Boolean get() = eligible > 0
}

/**
 * The setup screen shown when no session is running: what is due, what the scope
 * is, how big the session should be, and which modes can actually present these
 * cards.
 *
 * A mode with nothing to show is disabled and says why, rather than being offered
 * and producing an empty session. Tile mode silently skips long answers and
 * multiple choice silently skips topics with too few plausible siblings, so this
 * count is the only place that behaviour is visible before you commit to it.
 */
@Composable
private fun ModePicker(state: AppState) {
    val snapshot = state.snapshot.collectAsState().value
    val filter = state.filter.collectAsState().value
    val sessionSize = state.sessionSize.collectAsState().value
    val chosen = remember { mutableStateOf(StudyMode.MIXED) }
    val due = snapshot.dueCount(filter)
    val scoped = remember(snapshot, filter) { snapshot.filtered(filter) }
    val options = remember(snapshot, scoped) { modeOptions(snapshot, scoped) }
    val selected = options.firstOrNull { it.mode == chosen.value } ?: options.last()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppHeader(
            title = "Study",
            subtitle = due.toString() + " due now, " + scoped.size.toString() + " in scope",
            trailing = {
                TextButton(onClick = { state.navigate(Route.Library) }) { Text("Library") }
            },
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DueCard(state, due, scoped.size)
            ScopeRow(state, snapshot)

            SectionLabel("Session size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (size in AppState.SessionSizes) {
                    ToggleChip(
                        label = AppState.sessionSizeLabel(size),
                        selected = size == sessionSize,
                        onClick = { state.setSessionSize(size) },
                    )
                }
            }

            SectionLabel("Mode")
            for (row in options.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (option in row) {
                        ModeCard(option, option.mode == selected.mode, Modifier.weight(1f)) {
                            chosen.value = option.mode
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (!selected.isAvailable) {
                EmptyMessage(disabledReason(selected.mode))
            }

            Button(
                onClick = { state.startStudy(selected.mode) },
                enabled = selected.isAvailable,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start " + selected.mode.title.lowercase()) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DueCard(state: AppState, due: Int, inScope: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            due.toString() + " due today",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            if (due == 0) {
                "Nothing is due. You can still study the " + inScope.toString() +
                    " card(s) in scope to get ahead."
            } else {
                "From " + inScope.toString() + " card(s) in the current scope."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (due > 0) {
            Button(onClick = { state.startStudy(StudyMode.MIXED) }) { Text("Start due cards") }
        }
    }
}

/** Where this session will draw its cards from, and a way to go and change it. */
@Composable
private fun ScopeRow(state: AppState, snapshot: LibrarySnapshot) {
    val location = state.location.collectAsState().value
    val filter = state.filter.collectAsState().value
    val path = if (location == null) {
        "Everywhere"
    } else {
        snapshot.tree.breadcrumbs(location).joinToString("  \u203A  ") { it.name }
    }
    val tags = snapshot.tags.filter { it.id in filter.tagIds }.joinToString(", ") { it.name }

    Row(
        Modifier.fillMaxWidth().clickable { state.switchTab(Route.Library) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            SectionLabel("Scope")
            Text(path, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (tags.isEmpty()) "No tag filters" else "Tags: " + tags,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { state.switchTab(Route.Library) }) { Text("Change") }
    }
}

@Composable
private fun ModeCard(option: ModeOption, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .background(
                if (selected) scheme.primaryContainer.copy(alpha = 0.45f) else scheme.surfaceContainerLow,
                RoundedCornerShape(14.dp),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = option.isAvailable, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                option.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (option.isAvailable) scheme.primary else scheme.onSurfaceVariant,
            )
            Text(
                option.mode.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (option.isAvailable) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            option.description,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (option.isAvailable) option.eligible.toString() + " card(s)" else "none available",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (option.isAvailable) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

private fun modeOptions(snapshot: LibrarySnapshot, cards: List<Card>): List<ModeOption> =
    StudyMode.All.map { mode ->
        when (mode) {
            StudyMode.FLASHCARD -> ModeOption(
                mode = mode,
                eligible = cards.size,
                description = "Flip the card, then rate yourself",
                icon = Icons.Filled.Style,
            )

            StudyMode.TYPED -> ModeOption(
                mode = mode,
                eligible = cards.size,
                description = "Type it; graded, and you can override",
                icon = Icons.Filled.Keyboard,
            )

            StudyMode.TILES -> ModeOption(
                mode = mode,
                eligible = cards.count { TileQuestionFactory.isEligible(it) },
                description = "Put shuffled tiles back in order",
                icon = Icons.Filled.ViewModule,
            )

            StudyMode.MCQ -> ModeOption(
                mode = mode,
                eligible = cards.count { McqQuestionFactory.isEligible(it, snapshot.siblingsOf(it)) },
                description = "Pick one of four options",
                icon = Icons.Filled.Help,
            )

            StudyMode.MIXED -> ModeOption(
                mode = mode,
                eligible = cards.size,
                description = "Best-fit mode for each card",
                icon = Icons.Filled.Shuffle,
            )
        }
    }

private fun disabledReason(mode: StudyMode): String = when (mode) {
    StudyMode.TILES ->
        "No card in scope is short enough for tiles. Tile mode skips long answers rather than " +
            "turning a paragraph into fifty tiles."

    StudyMode.MCQ ->
        "No card in scope can make four distinct options. A card needs three authored distractors, " +
            "or three sibling cards in the same topic with the same answer type."

    StudyMode.FLASHCARD, StudyMode.TYPED, StudyMode.MIXED ->
        "Nothing in scope. Widen the location or clear a filter in the Library."
}

// ------------------------------------------------------------------ asking ---

@Composable
private fun Asking(state: AppState, asking: SessionState.Asking) {
    val item = asking.item
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SessionHeader(state, asking.position, asking.total, item)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Prompt(state, item)
            when (val question = item.question) {
                is Question.Flashcard -> FlashcardInput(state, item, asking.draft)
                is Question.Typed -> TypedInput(state, asking.draft)
                is Question.Tiles -> TilesInput(state, asking.draft, question)
                is Question.MultipleChoice -> McqInput(state, question)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SessionHeader(state: AppState, position: Int, total: Int, item: SessionCard) {
    val snapshot = state.snapshot.collectAsState().value
    val topicName = snapshot.topics.firstOrNull { it.id == item.card.topicId }?.name ?: "Unknown topic"
    AppHeader(
        title = item.mode.title,
        subtitle = "Card " + (position + 1) + " of " + total + " - " + topicName,
        onBack = { state.endSession() },
        trailing = {
            if (item.schedule.isNew) {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "NEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                    )
                }
            }
        },
    )
    val fraction = if (total <= 0) 0f else position.toFloat() / total
    Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(MaterialTheme.colorScheme.primary))
    }
}

/** The question itself: always the card front, never the answer. */
@Composable
private fun Prompt(state: AppState, item: SessionCard) {
    Panel {
        SectionLabel(if (item.mode == StudyMode.FLASHCARD) "Front" else "Question")
        MathText(item.card.front, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun FlashcardInput(state: AppState, item: SessionCard, draft: AnswerDraft) {
    when (draft) {
        AnswerDraft.Hidden -> Button(
            onClick = { state.onSessionEvent(SessionEvent.Reveal) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Show answer") }

        AnswerDraft.Revealed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModelAnswer(state, item)
            SectionLabel("How well did you know it?")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingButton("Again", Modifier.weight(1f)) { state.onSessionEvent(SessionEvent.Rate(Rating.AGAIN)) }
                RatingButton("Hard", Modifier.weight(1f)) { state.onSessionEvent(SessionEvent.Rate(Rating.HARD)) }
                RatingButton("Good", Modifier.weight(1f)) { state.onSessionEvent(SessionEvent.Rate(Rating.GOOD)) }
                RatingButton("Easy", Modifier.weight(1f)) { state.onSessionEvent(SessionEvent.Rate(Rating.EASY)) }
            }
        }

        is AnswerDraft.Text, is AnswerDraft.Tiles, AnswerDraft.Choice ->
            EmptyMessage("This card is not a flashcard; restart the session.")
    }
}

@Composable
private fun RatingButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun TypedInput(state: AppState, draft: AnswerDraft) {
    val typed = draft as? AnswerDraft.Text ?: AnswerDraft.Text("")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = typed.value,
            onValueChange = { state.onSessionEvent(SessionEvent.Type(it)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("Type your answer...") },
        )
        Button(
            onClick = { state.onSessionEvent(SessionEvent.SubmitText) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Check answer") }
    }
}

@Composable
private fun TilesInput(state: AppState, draft: AnswerDraft, question: Question.Tiles) {
    val chosen = draft as? AnswerDraft.Tiles ?: AnswerDraft.Tiles(emptyList())
    val chosenIds = chosen.chosen.toSet()
    val ordered = chosen.chosen.mapNotNull { id -> question.tiles.firstOrNull { it.id == id } }
    val remaining = question.tiles.filter { it.id !in chosenIds }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Your answer (" + ordered.size + " of " + question.solution.size + " tiles)")
        Panel {
            if (ordered.isEmpty()) {
                Text(
                    "Tap the tiles below to build the answer, in order.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (row in ordered.chunked(TILES_PER_ROW)) {
                TileRow(row, selected = true) { state.onSessionEvent(SessionEvent.TapTile(it)) }
            }
        }
        SectionLabel("Tiles")
        for (row in remaining.chunked(TILES_PER_ROW)) {
            TileRow(row, selected = false) { state.onSessionEvent(SessionEvent.TapTile(it)) }
        }
        if (question.decoyCount > 0) {
            EmptyMessage(question.decoyCount.toString() + " of these tiles do not belong in the answer.")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { state.onSessionEvent(SessionEvent.ClearTiles) }) { Text("Clear") }
            Button(
                onClick = { state.onSessionEvent(SessionEvent.SubmitTiles) },
                modifier = Modifier.weight(1f),
                enabled = ordered.isNotEmpty(),
            ) { Text("Check answer") }
        }
    }
}

/** One wrapped row of tiles. Tap to add a tile to the answer, or take it back out. */
@Composable
private fun TileRow(tiles: List<Tile>, selected: Boolean, onTap: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (tile in tiles) {
            ToggleChip(
                label = tile.text,
                selected = selected,
                onClick = { onTap(tile.id) },
            )
        }
    }
}

@Composable
private fun McqInput(state: AppState, question: Question.MultipleChoice) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Choose one")
        for (option in question.options) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable { state.onSessionEvent(SessionEvent.ChooseOption(option.index)) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row {
                    Text(
                        (option.index + 1).toString() + ".  ",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    MathText(option.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- reviewing ---

@Composable
private fun Reviewing(state: AppState, reviewing: SessionState.Reviewing) {
    val item = reviewing.item
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SessionHeader(state, reviewing.position, reviewing.total, item)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Prompt(state, item)
            ModelAnswer(state, item)
            VerdictPanel(reviewing.verdict)
            NextActions(state, item, reviewing.verdict)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The card's back, its explanation and any spec reference. */
@Composable
private fun ModelAnswer(state: AppState, item: SessionCard) {
    val card = item.card
    Panel {
        SectionLabel("Model answer")
        MathText(card.back, style = MaterialTheme.typography.bodyLarge)
        val explanation = card.explanation
        if (!explanation.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Why")
            MathText(explanation, style = MaterialTheme.typography.bodyMedium)
        }
        if (card.specRef.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Spec reference: " + card.specRef,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VerdictPanel(verdict: Verdict) {
    val colour = when (verdict.kind) {
        VerdictKind.CORRECT -> CorrectGreen
        VerdictKind.PARTIAL -> PartialAmber
        VerdictKind.INCORRECT -> MaterialTheme.colorScheme.error
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(colour.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, colour, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                VerdictPresentation.label(verdict.kind) + "  -  " + VerdictPresentation.percent(verdict.score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colour,
            )
            MathText(VerdictPresentation.describe(verdict.reason), style = MaterialTheme.typography.bodyMedium)
            KeyPointLine("Covered", verdict.matchedKeyPoints, MaterialTheme.typography.bodySmall, Color.Unspecified)
            KeyPointLine(
                "Missing",
                verdict.missedKeyPoints,
                MaterialTheme.typography.bodySmall,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Covered: a, b" with the key points themselves rendered as maths. */
@Composable
private fun KeyPointLine(label: String, points: List<String>, style: TextStyle, colour: Color) {
    if (points.isEmpty()) return
    Row {
        Text("$label: ", style = style, color = colour)
        MathText(points.joinToString(", "), style = style, color = colour)
    }
}

@Composable
private fun NextActions(state: AppState, item: SessionCard, verdict: Verdict) {
    if (verdict.requiresSelfGrade) {
        // The checker declined to decide, so "Next" would do nothing: only the
        // user can grade this card.
        EmptyMessage("This card needs your judgement - the automatic checker could not decide.")
        OverrideRow(state)
        return
    }
    Button(
        onClick = { state.onSessionEvent(SessionEvent.Next) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Next card") }
    if (item.mode == StudyMode.TYPED) {
        SectionLabel("Disagree with the verdict?")
        OverrideRow(state)
    }
}

@Composable
private fun OverrideRow(state: AppState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { state.onSessionEvent(SessionEvent.Override(true)) },
            modifier = Modifier.weight(1f),
        ) { Text("I was right") }
        OutlinedButton(
            onClick = { state.onSessionEvent(SessionEvent.Override(false)) },
            modifier = Modifier.weight(1f),
        ) { Text("I was wrong") }
    }
}

// ----------------------------------------------------------------- summary ---

@Composable
private fun Finished(state: AppState, finished: SessionState.Finished) {
    val summary = finished.summary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppHeader(title = "Session complete", subtitle = summary.reviewed.toString() + " card(s) reviewed")
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Reviewed", summary.reviewed.toString(), Modifier.weight(1f))
                StatTile("Accuracy", VerdictPresentation.percent(summary.accuracy), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Correct", summary.correct.toString(), Modifier.weight(1f))
                StatTile("Partly right", summary.partial.toString(), Modifier.weight(1f))
                StatTile("Incorrect", summary.incorrect.toString(), Modifier.weight(1f))
            }
            EmptyMessage(
                "Every answer was written to the same spaced-repetition schedule, whichever mode it " +
                    "came from. Cards answered in a harder mode earn a longer interval.",
            )
            Button(onClick = { state.endSession() }, modifier = Modifier.fillMaxWidth()) { Text("Back to browse") }
            OutlinedButton(
                onClick = { state.navigate(Route.Stats) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("See stats") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ------------------------------------------------------------------ shared ---

/** A rounded, lightly filled container for one block of content. */
@Composable
private fun Panel(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}
