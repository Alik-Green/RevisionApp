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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.check.Verdict
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.srs.Rating
import com.revisionapp.domain.study.Question
import com.revisionapp.domain.study.Tile
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

@Composable
private fun ModePicker(state: AppState) {
    val snapshot = state.snapshot.collectAsState().value
    val filter = state.filter.collectAsState().value
    val due = snapshot.dueCount(filter)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppHeader(
            title = "Study",
            subtitle = due.toString() + " card(s) due with the current filters",
            trailing = {
                TextButton(onClick = { state.navigate(Route.Library) }) { Text("Library") }
            },
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EmptyMessage(
                if (due == 0) {
                    "Nothing is due right now. Loosen the filters on the Library screen, or start a " +
                        "session anyway - it will simply be empty."
                } else {
                    "Filters are shared with the Library screen: a session only ever contains cards the " +
                        "current topic and tag selection allows. Cards the chosen mode cannot present - " +
                        "a long paragraph in tile mode, a topic with too few siblings for multiple " +
                        "choice - drop out silently."
                },
            )
            for (mode in StudyMode.All) {
                OutlinedButton(
                    onClick = { state.startStudy(mode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            mode.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            describeMode(mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun describeMode(mode: StudyMode): String = when (mode) {
    StudyMode.FLASHCARD -> "Flip the card, then rate yourself Again / Hard / Good / Easy."
    StudyMode.TYPED -> "Type the answer. Graded automatically, and you can always override the verdict."
    StudyMode.TILES -> "Put shuffled word tiles back in order. Short answers only."
    StudyMode.MCQ -> "Pick one of four options, with the explanation shown straight away."
    StudyMode.MIXED -> "Chooses the best-fit mode for each card, and gets harder after a mistake."
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
