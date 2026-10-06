package com.revisionapp.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.TopicAccuracyRow
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.StatTile
import com.revisionapp.ui.session.VerdictPresentation

/** Due today, streak and per-topic accuracy. Reloaded every time it is opened. */
@Composable
fun StatsScreen(state: AppState) {
    val stats = state.stats.collectAsState().value
    LaunchedEffect(Unit) { state.loadStats() }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Stats",
            subtitle = stats.totalCards.toString() + " cards, " + stats.userCards.toString() + " of them yours",
            trailing = {
                TextButton(onClick = { state.navigate(Route.Browse) }) { Text("Browse") }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Due today", stats.dueToday.toString(), Modifier.weight(1f))
                StatTile("Due now", stats.dueNow.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Day streak", stats.streakDays.toString(), Modifier.weight(1f))
                StatTile("Days studied", stats.daysStudied.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Total reviews", stats.totalReviews.toString(), Modifier.weight(1f))
                StatTile("Cards", stats.totalCards.toString(), Modifier.weight(1f))
            }

            HorizontalDivider()
            SectionLabel("Accuracy by topic")
            if (stats.accuracy.isEmpty()) {
                EmptyMessage(
                    "No reviews recorded yet. Accuracy counts every mode together, so a correct typed " +
                        "answer and a correct multiple-choice answer both land here.",
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
private fun AccuracyRow(row: TopicAccuracyRow) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                row.topicName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
            Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier.fillMaxWidth(fraction).height(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}
