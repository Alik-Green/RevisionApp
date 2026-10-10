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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.CharacterAvatar
import com.revisionapp.ui.components.SectionLabel

/** Local learner profile with an editable name and earned-only character styling. */
@Composable
fun ProfileScreen(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val progressionReady = state.progressionReady.collectAsState().value
    val catalog = state.courseCatalog.collectAsState().value
    val activeCourse = catalog.course(progress.activeCourseId)
    val nameDraft = remember(progress.displayName) { mutableStateOf(progress.displayName) }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Profile",
            subtitle = "Your learning space",
            trailing = {
                IconButton(onClick = { state.navigate(Route.Settings) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Open settings")
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CharacterAvatar(progress.characterAppearance, size = 84.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(progress.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Your learner character",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text("🪙 ${progress.coins} coins", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionLabel("YOUR NAME")
                    OutlinedTextField(
                        value = nameDraft.value,
                        onValueChange = { nameDraft.value = it.take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Display name") },
                        enabled = progressionReady,
                    )
                    Button(
                        onClick = {
                            val name = nameDraft.value.trim().take(24).ifBlank { "Learner" }
                            nameDraft.value = name
                            state.setDisplayName(name)
                        },
                        enabled = progressionReady,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save name") }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("ACTIVE COURSE")
                    Text(activeCourse?.name ?: "Choose a course", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        activeCourse?.description ?: "Browse Courses in the Store and download a learning path to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = {
                            if (activeCourse == null) state.openCourseStore() else state.switchTab(Route.Study)
                        },
                        enabled = progressionReady,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (activeCourse == null) "Open Store" else "Go to Study") }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("YOUR CHARACTER")
                    Text("Design your learner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Choose skin tone, hair, eyes and nose. Shape and position unlocked features for free.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Button(
                        onClick = { state.navigate(Route.CharacterCustomizer) },
                        enabled = progressionReady,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Customize appearance") }
                    OutlinedButton(
                        onClick = { state.openCosmeticsStore() },
                        enabled = progressionReady,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Browse cosmetic parts") }
                }
            }

            OutlinedButton(
                onClick = { state.navigate(Route.Library) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open card library") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
