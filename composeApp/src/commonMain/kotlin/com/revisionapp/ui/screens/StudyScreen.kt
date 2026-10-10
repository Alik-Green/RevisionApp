package com.revisionapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.check.Verdict
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.check.VerdictReason
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.srs.Rating
import com.revisionapp.domain.study.Question
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
import com.revisionapp.ui.session.SessionSummary
import com.revisionapp.ui.session.VerdictPresentation
import com.revisionapp.ui.theme.verdictColour
import com.revisionapp.ui.theme.verdictContainerColour
import com.revisionapp.ui.theme.verdictOnContainerColour

/**
 * The study screen. It renders [SessionState] with an exhaustive `when`, so a new
 * session state cannot be forgotten here, and delegates every input straight back
 * to [AppState.onSessionEvent] — the screen holds no logic of its own.
 */
@Composable
fun StudyScreen(state: AppState) {
    val session = state.sessionState.collectAsState().value
    when (session) {
        SessionState.Empty -> StudySetup(state)
        is SessionState.Asking -> Asking(state, session)
        is SessionState.Reviewing -> Reviewing(state, session)
        is SessionState.Finished -> Finished(state, session)
    }
}

// ------------------------------------------------------------------ setup ---

/**
 * The setup screen shows the scope and session size. Question presentation is
 * selected automatically for each card; learners never have to guess which mode
 * fits a question.
 */
@Composable
private fun StudySetup(state: AppState) {
    val snapshot = state.snapshot.collectAsState().value
    val filter = state.filter.collectAsState().value
    val sessionSize = state.sessionSize.collectAsState().value
    val due = snapshot.dueCount(filter)
    val scoped = remember(snapshot, filter) { snapshot.filtered(filter) }

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
            DueCard(due, scoped.size)
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
            EmptyMessage(
                "We'll choose the best fit for every card automatically: typed answers when they can be " +
                    "checked well, multiple choice when good options exist, and flashcards when self-checking " +
                    "is fairest.",
            )
            Button(
                onClick = { state.startStudy() },
                enabled = scoped.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (due > 0) "Start due cards" else "Study ahead") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DueCard(due: Int, inScope: Int) {
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
                if (inScope == 0) {
                    "Nothing is in this scope yet. Try a different topic or clear a filter."
                } else {
                    "Nothing is due. You can study the " + inScope.toString() + " card(s) in scope to get ahead."
                }
            } else {
                "From " + inScope.toString() + " card(s) in the current scope."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
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
                is Question.Tiles -> EmptyMessage("This retired question format is no longer available. Restart the lesson to continue.")
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

        is AnswerDraft.Text, AnswerDraft.Unsupported, AnswerDraft.Choice ->
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
            val question = item.question
            if (question is Question.MultipleChoice) {
                McqReview(question, reviewing.verdict)
            }
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
    val question = item.question
    // For multiple choice the answer is the correct option, which an authored
    // mcq.correct is allowed to differ from.
    val answer = if (question is Question.MultipleChoice) question.correctText else card.back
    Panel {
        SectionLabel("Model answer")
        MathText(answer, style = MaterialTheme.typography.bodyLarge)
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

/**
 * The four options again, after answering: the correct one in green, and the one you
 * chose in red if it was wrong. The verdict text can say "option 1 is correct" all it
 * likes; with the options gone from the screen there is nothing to read it against.
 */
@Composable
private fun McqReview(question: Question.MultipleChoice, verdict: Verdict) {
    val selection = verdict.reason as? VerdictReason.McqSelection
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("The options")
        for (option in question.options) {
            val chosen = selection?.selectedIndex == option.index
            val kind = when {
                option.isCorrect -> VerdictKind.CORRECT
                chosen -> VerdictKind.INCORRECT
                else -> null
            }
            val container = if (kind == null) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                verdictContainerColour(kind)
            }
            val onContainer = if (kind == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                verdictOnContainerColour(kind)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(container, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    (option.index + 1).toString() + ".",
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer,
                )
                MathText(
                    option.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                    modifier = Modifier.weight(1f),
                )
                if (option.isCorrect) {
                    Icon(Icons.Filled.Check, contentDescription = "Correct answer", tint = onContainer)
                } else if (chosen) {
                    Icon(Icons.Filled.Close, contentDescription = "Your answer", tint = onContainer)
                }
            }
        }
    }
}

@Composable
private fun VerdictPanel(verdict: Verdict) {
    val colour = verdictColour(verdict.kind)
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
        AppHeader(title = "Lesson complete!", subtitle = summary.reviewed.toString() + " card(s) reviewed")
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (summary.streakDays > 0) {
                StreakCelebration(summary)
            }
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

/** Animated streak milestone shown when a lesson ends. */
@Composable
private fun StreakCelebration(summary: SessionSummary) {
    val visible = remember(summary.streakDays) { mutableStateOf(false) }
    LaunchedEffect(summary.streakDays) { visible.value = true }
    val message = when {
        !summary.streakAdvanced -> "You kept your streak going today. Keep the rhythm tomorrow!"
        summary.streakDays == 1 -> "A new streak starts today. Come back tomorrow to keep it alive!"
        else -> "Streak extended to ${summary.streakDays} days. Come back tomorrow to keep it alive!"
    }
    AnimatedVisibility(
        visible = visible.value,
        enter = fadeIn(animationSpec = tween(450)) + scaleIn(
            initialScale = 0.88f,
            animationSpec = tween(450),
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🔥", style = MaterialTheme.typography.headlineLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("STREAK CELEBRATION")
                Text(
                    summary.streakDays.toString() + if (summary.streakDays == 1) " day" else " days",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

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
