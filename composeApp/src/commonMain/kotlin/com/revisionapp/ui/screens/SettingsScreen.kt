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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.revisionapp.ui.theme.appInset
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.revisionapp.ui.components.AppButton as Button
import com.revisionapp.ui.components.AppOutlinedButton as OutlinedButton
import com.revisionapp.ui.components.AppTextButton as TextButton

/**
 * Content sync status and controls, the retention setting and where the database
 * lives. Sync errors are surfaced here in full, as the brief requires.
 */
@Composable
fun SettingsScreen(state: AppState) {
    val settings = state.settingsUi.collectAsState().value
    val sync = state.syncState.collectAsState().value
    val progress = state.learnerProgress.collectAsState().value
    val progressionReady = state.progressionReady.collectAsState().value
    val urlDraft = remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    val retention = remember(settings.desiredRetention) { mutableStateOf(settings.desiredRetention.toFloat()) }
    val developerCode = remember { mutableStateOf("") }
    val developerCodeError = remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Settings",
            subtitle = state.platformName,
            onBack = { state.back() },
            trailing = {
                TextButton(onClick = { state.refresh() }) { Text("Reload") }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = appInset(16.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(12.dp)),
        ) {
            SectionLabel("Appearance")
            Text("Light or dark", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
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

            SectionLabel("Sound effects")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Correct/incorrect answers, lesson, quest and streak milestones",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    modifier = Modifier.semantics { contentDescription = "Sound effects" },
                    checked = settings.soundEffectsEnabled,
                    onCheckedChange = { state.setSoundEffectsEnabled(it) },
                )
            }

            HorizontalDivider()
            SectionLabel("V2 course store")
            EmptyMessage(
                "The Store lists courses from this repository. The app does not download courses " +
                    "at startup: open the Store and choose a course to download and cache it for offline use.",
            )
            OutlinedButton(onClick = { state.openCourseStore() }, modifier = Modifier.fillMaxWidth()) {
                Text("Open course store")
            }

            HorizontalDivider()
            SectionLabel("Legacy card packs")
            SyncStatus(sync)
            Button(
                onClick = { state.syncNow() },
                enabled = sync != SyncUiState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (sync == SyncUiState.Running) "Syncing..." else "Sync legacy packs") }
            LabeledField(
                label = "Legacy pack source URL",
                value = urlDraft.value,
                onValueChange = { urlDraft.value = it },
                hint = ContentSync.DEFAULT_BASE_URL,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
                Button(onClick = { state.setBaseUrl(urlDraft.value) }) { Text("Save source") }
                TextButton(
                    onClick = {
                        urlDraft.value = ContentSync.DEFAULT_BASE_URL
                        state.setBaseUrl(ContentSync.DEFAULT_BASE_URL)
                    },
                ) { Text("Reset source") }
            }
            EmptyMessage(
                "This URL is only for legacy card packs used by the card library. V2 courses use the " +
                    "separate Course Store. Legacy packs are downloaded " +
                    "from the `content` branch and verified against SHA-256 checksums. Only changed " +
                    "packs are fetched; imports are transactional, and a failed import leaves the " +
                    "previous version in place. After syncing, the card library also works offline. " +
                    "A private source needs authentication; avoid saving a secret-bearing URL on a shared device.",
            )

            SectionLabel("Installed legacy packs (" + settings.packs.size + ")")
            if (settings.packs.isEmpty()) {
                EmptyMessage("No legacy packs installed. Tap \"Sync legacy packs\" to fetch them.")
            }
            for (pack in settings.packs) {
                Column(Modifier.fillMaxWidth().padding(vertical = appInset(3.dp))) {
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
            MetaRow("Legacy pack source URL", settings.baseUrl)

            HorizontalDivider()
            SectionLabel("Developer mode")
            if (progress.developerMode) {
                EmptyMessage(
                    "Developer mode is on. Your balance is unlimited, and all current cosmetic parts are unlocked. " +
                        "This setting is saved on this device; the code itself is never stored.",
                )
                OutlinedButton(
                    onClick = { state.setDeveloperMode(false) },
                    enabled = progressionReady,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Turn off developer mode") }
            } else {
                Text(
                    "Enter a developer code for unlimited coins and cosmetic unlocks. This local convenience " +
                        "is not a security feature.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = developerCode.value,
                    onValueChange = {
                        developerCode.value = it.take(64)
                        developerCodeError.value = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Developer code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = progressionReady,
                )
                if (developerCodeError.value) {
                    Text(
                        "That code was not recognised.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = {
                        if (state.unlockDeveloperMode(developerCode.value)) {
                            developerCode.value = ""
                            developerCodeError.value = false
                        } else {
                            developerCodeError.value = true
                        }
                    },
                    enabled = progressionReady && developerCode.value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enable developer mode") }
            }
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
        SyncUiState.Never -> EmptyMessage("Legacy card packs have never been synced on this device.")

        SyncUiState.Running -> EmptyMessage("Checking the legacy content branch for updates...")

        is SyncUiState.Done -> Column(verticalArrangement = Arrangement.spacedBy(appInset(4.dp))) {
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

        is SyncUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(appInset(4.dp))) {
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
