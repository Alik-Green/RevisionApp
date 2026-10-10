package com.revisionapp.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.model.TopicNode
import com.revisionapp.domain.usecase.LibrarySnapshot
import com.revisionapp.domain.usecase.WeeklyQuestProgress
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.TopicAccuracyRow
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.AppTextButton as TextButton
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.MathText
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.StatTile
import com.revisionapp.ui.session.VerdictPresentation
import com.revisionapp.ui.theme.appCornerShape
import com.revisionapp.ui.theme.appHeadingWeight
import com.revisionapp.ui.theme.appInset

/** Due today, streak, weekly quests and topic-tree progress. */
@Composable
fun StatsScreen(state: AppState) {
    val stats = state.stats.collectAsState().value
    val snapshot = state.snapshot.collectAsState().value
    LaunchedEffect(Unit) { state.loadStats() }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Stats",
            subtitle = stats.totalCards.toString() + " cards, " + stats.userCards.toString() + " of them yours",
            trailing = {
                TextButton(onClick = { state.navigate(Route.Library) }) { Text("Library") }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = appInset(16.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(12.dp)),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(appInset(10.dp))) {
                StatTile("Due today", stats.dueToday.toString(), Modifier.weight(1f))
                StatTile("Due now", stats.dueNow.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(appInset(10.dp))) {
                StatTile("Day streak", stats.streakDays.toString(), Modifier.weight(1f))
                StatTile("Days studied", stats.daysStudied.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(appInset(10.dp))) {
                StatTile("Total reviews", stats.totalReviews.toString(), Modifier.weight(1f))
                StatTile("Quest points", stats.questPoints.toString(), Modifier.weight(1f))
            }

            HorizontalDivider()
            SectionLabel("Weekly quests")
            Text(
                stats.studiedDaysThisWeek.toString() + " distinct study day(s) so far this week",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (quest in stats.weeklyQuests) {
                WeeklyQuestRow(quest)
            }
            EmptyMessage("Points are earned automatically from your review history; they never change card scheduling.")

            HorizontalDivider()
            SectionLabel("Topic mastery tree")
            EmptyMessage(
                "A first progress map using your current topics. Coverage shows cards practised and due for review; " +
                    "it is not a formal mastery score or a new V2 lesson sequence yet.",
            )
            if (snapshot.tree.roots.isEmpty()) {
                EmptyMessage("Your topic tree will appear here after content is installed or a topic is added.")
            }
            for (root in snapshot.tree.roots) {
                TopicTreeProgress(state, snapshot, root, depth = 0)
            }

            HorizontalDivider()
            SectionLabel("Accuracy by topic")
            if (stats.accuracy.isEmpty()) {
                EmptyMessage(
                    "No reviews recorded yet. Accuracy counts every supported question style together, " +
                        "including self-rated flashcards.",
                )
            }
            for (row in stats.accuracy) {
                AccuracyRow(row)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WeeklyQuestRow(quest: WeeklyQuestProgress) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, appCornerShape(14.dp))
            .padding(appInset(12.dp)),
        verticalArrangement = Arrangement.spacedBy(appInset(7.dp)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
            Column(Modifier.weight(1f)) {
                Text(quest.title, style = MaterialTheme.typography.titleSmall, fontWeight = appHeadingWeight())
                Text(
                    quest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (quest.isComplete) "✓ ${quest.rewardPoints} pts" else "+${quest.rewardPoints} pts",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, appCornerShape(4.dp)),
        ) {
            if (quest.progressFraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(quest.progressFraction)
                        .height(7.dp)
                        .background(MaterialTheme.colorScheme.primary, appCornerShape(4.dp)),
                )
            }
        }
        Text(
            if (quest.isComplete) "Complete — reward earned" else "${quest.studiedDays} / ${quest.targetStudyDays} study days",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class TopicPracticeProgress(
    val totalCards: Int,
    val practisedCards: Int,
    val dueCards: Int,
) {
    val fraction: Float
        get() = if (totalCards == 0) 0f else practisedCards.toFloat() / totalCards

    val status: String
        get() = when {
            totalCards == 0 -> "No cards"
            practisedCards == 0 -> "Not started"
            dueCards > 0 -> "$dueCards due for review"
            practisedCards < totalCards -> "In progress"
            else -> "Up to date"
        }
}

@Composable
private fun TopicTreeProgress(state: AppState, snapshot: LibrarySnapshot, node: TopicNode, depth: Int) {
    val expanded = remember(node.id) { mutableStateOf(false) }
    val topicIds = snapshot.tree.descendantsIncluding(node.id)
    val cards = snapshot.cards.filter { it.topicId in topicIds }
    val progress = TopicPracticeProgress(
        totalCards = cards.size,
        practisedCards = cards.count { !snapshot.isNew(it) },
        dueCards = cards.count { !snapshot.isNew(it) && snapshot.isDue(it) },
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = (depth * 12).dp, top = appInset(3.dp), bottom = appInset(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow, appCornerShape(12.dp))
                .padding(horizontal = appInset(12.dp), vertical = appInset(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(appInset(8.dp)),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable {
                        state.openTopic(node.id)
                        state.switchTab(Route.Library)
                    },
                verticalArrangement = Arrangement.spacedBy(appInset(2.dp)),
            ) {
                MathText(
                    node.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = appHeadingWeight()),
                    maxLines = 1,
                )
                Text(
                    "${progress.practisedCards} / ${progress.totalCards} practised · ${progress.status}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (node.children.isNotEmpty()) {
                TextButton(onClick = { expanded.value = !expanded.value }) {
                    Text(if (expanded.value) "Hide" else "Explore")
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = (depth * 12).dp)
                .height(5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, appCornerShape(3.dp)),
        ) {
            if (progress.fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.fraction.coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.primary, appCornerShape(3.dp)),
                )
            }
        }
        if (expanded.value) {
            for (child in node.children) {
                TopicTreeProgress(state, snapshot, child, depth + 1)
            }
        }
    }
}

@Composable
private fun AccuracyRow(row: TopicAccuracyRow) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = appInset(4.dp)),
        verticalArrangement = Arrangement.spacedBy(appInset(4.dp)),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
            MathText(
                row.topicName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                row.correct.toString() + "/" + row.reviews.toString() + "  " +
                    VerdictPresentation.percent(row.accuracy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val fraction = row.accuracy.toFloat().coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, appCornerShape(3.dp)),
        ) {
            Box(
                Modifier.fillMaxWidth(fraction).height(6.dp).background(MaterialTheme.colorScheme.primary, appCornerShape(3.dp)),
            )
        }
    }
}
