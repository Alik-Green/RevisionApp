package com.revisionapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.data.sync.ContentSync
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.SettingsUi
import com.revisionapp.ui.SyncUiState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.LabeledField
import com.revisionapp.ui.components.MetaRow
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip
import com.revisionapp.ui.session.VerdictPresentation
import com.revisionapp.ui.theme.ThemeMode
import com.revisionapp.ui.theme.ThemeStyle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Content sync status and controls, the retention setting and where the database
 * lives. Sync errors are surfaced here in full, as the brief requires.
 */
@Composable
fun SettingsScreen(state: AppState) {
    val settings = state.settingsUi.collectAsState().value
    val sync = state.syncState.collectAsState().value
    val urlDraft = remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    val retention = remember(settings.desiredRetention) { mutableStateOf(settings.desiredRetention.toFloat()) }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Settings",
            subtitle = state.platformName,
            trailing = {
                TextButton(onClick = { state.refresh() }) { Text("Reload") }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Appearance")
            Text("Colour style", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (style in ThemeStyle.entries) {
                    ToggleChip(
                        label = style.title,
                        selected = style == settings.themeStyle,
                        onClick = { state.setThemeStyle(style) },
                    )
                }
            }
            Text("Light or dark", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in ThemeMode.entries) {
                    ToggleChip(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        selected = mode == settings.themeMode,
                        onClick = { state.setThemeMode(mode) },
                    )
                }
            }
            EmptyMessage("Playful is the default; Ink & Paper keeps the original quieter palette.")

            HorizontalDivider()
            SectionLabel("Content packs")
            SyncStatus(sync)
            Button(
                onClick = { state.syncNow() },
                enabled = sync != SyncUiState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (sync == SyncUiState.Running) "Syncing..." else "Sync now") }
            LabeledField(
                label = "Content base URL",
                value = urlDraft.value,
                onValueChange = { urlDraft.value = it },
                hint = ContentSync.DEFAULT_BASE_URL,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { state.setBaseUrl(urlDraft.value) }) { Text("Save URL") }
                TextButton(
                    onClick = {
                        urlDraft.value = ContentSync.DEFAULT_BASE_URL
                        state.setBaseUrl(ContentSync.DEFAULT_BASE_URL)
                    },
                ) { Text("Reset to default") }
            }
            EmptyMessage(
                "Packs are downloaded from the `content` branch of a public GitHub repository and " +
                    "verified against their SHA-256 checksums. Only packs whose version or checksum " +
                    "changed are fetched, everything is imported in one transaction, and a failed " +
                    "import leaves the previous version in place. The app is fully offline after a " +
                    "successful sync. A private repository needs a personal access token in the URL, " +
                    "which this app does not store for you.",
            )

            SectionLabel("Installed packs (" + settings.packs.size + ")")
            if (settings.packs.isEmpty()) {
                EmptyMessage("Nothing installed yet. Tap \"Sync now\" to fetch the built-in content packs.")
            }
            for (pack in settings.packs) {
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        pack.name + "  v" + pack.version.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        pack.id.value + "  -  " + pack.cardCount.toString() + " cards  -  sha256 " +
                            pack.sha256.take(12),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()
            SectionLabel("Spaced repetition")
            Text(
                "Desired retention: " + VerdictPresentation.percent(retention.value.toDouble()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = retention.value,
                onValueChange = { retention.value = it },
                onValueChangeFinished = { state.setDesiredRetention(retention.value.toDouble()) },
                valueRange = SettingsUi.MIN_RETENTION.toFloat()..SettingsUi.MAX_RETENTION.toFloat(),
            )
            EmptyMessage(
                "FSRS-4.5 with its published default weights. A higher target retention schedules " +
                    "cards more often. Changing this affects future reviews only; the history that " +
                    "has already been recorded is left alone.",
            )

            HorizontalDivider()
            SectionLabel("Storage")
            MetaRow("Platform", state.platformName)
            MetaRow("Database", state.dataDirectory)
            MetaRow("Content base URL", settings.baseUrl)

            HorizontalDivider()
            SectionLabel("Developer")
            EmptyMessage(
                "Diagnostics for the parts of this app that cannot be seen while it is being " +
                    "written: there is no display in the sandbox it is developed in.",
            )
            OutlinedButton(
                onClick = { state.navigate(Route.MathGallery) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Math gallery") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Exhaustive over [SyncUiState], so a new sync outcome cannot go unreported. */
@Composable
private fun SyncStatus(sync: SyncUiState) {
    when (sync) {
        SyncUiState.Never -> EmptyMessage("Content has never been synced on this device.")

        SyncUiState.Running -> EmptyMessage("Checking the content branch for updates...")

        is SyncUiState.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Last sync: " + formatTime(sync.at), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Updated " + sync.report.updated.size + ", unchanged " + sync.report.unchanged.size +
                    ", errors " + sync.report.errors.size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (error in sync.report.errors) {
                Text(
                    error.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is SyncUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Last sync failed: " + formatTime(sync.at), style = MaterialTheme.typography.bodyMedium)
            Text(
                sync.error.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatTime(at: Instant): String =
    at.toLocalDateTime(TimeZone.currentSystemDefault()).toString().substringBefore('.')
