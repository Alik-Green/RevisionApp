package com.revisionapp.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppButton as Button
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.AppOutlinedButton as OutlinedButton
import com.revisionapp.ui.components.CharacterAvatar
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.theme.ExtendedTheme
import com.revisionapp.ui.theme.appCornerShape
import com.revisionapp.ui.theme.appHeadingWeight
import com.revisionapp.ui.theme.appInset

/** Local learner profile with an editable name and earned-only character styling. */
@Composable
fun ProfileScreen(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val progressionReady = state.progressionReady.collectAsState().value
    val catalog = state.courseCatalog.collectAsState().value
    val activeCourse = catalog.course(progress.activeCourseId)
    val nameDraft = remember(progress.displayName) { mutableStateOf(progress.displayName) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

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
                .padding(horizontal = appInset(20.dp), vertical = appInset(8.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(18.dp)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = appCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            ) {
                Row(
                    Modifier.padding(appInset(16.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(appInset(14.dp)),
                ) {
                    Box(Modifier.size(84.dp)) {
                        CharacterAvatar(progress.characterAppearance, modifier = Modifier.align(Alignment.Center), size = 84.dp)
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).size(34.dp)
                                .clickable { state.navigate(Route.CharacterCustomizer) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit avatar", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(appInset(4.dp))) {
                        Text(progress.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Your learner character",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "🪙 ${progress.coinBalanceLabel} coins",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isDark) ExtendedTheme.colors.reward else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = appCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            ) {
                Column(
                    Modifier.padding(appInset(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(appInset(10.dp)),
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
                shape = appCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            ) {
                Row(
                    Modifier.padding(horizontal = appInset(15.dp), vertical = appInset(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(appInset(10.dp)),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appInset(3.dp))) {
                        SectionLabel("ACTIVE COURSE")
                        Text(activeCourse?.name ?: "Choose a course", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (activeCourse != null) {
                            Text(
                                "${activeCourse.orderedLessons().size} lessons",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (activeCourse == null) state.openCourseStore() else state.switchTab(Route.Study)
                        },
                        enabled = progressionReady,
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = if (activeCourse == null) "Choose a course in Store" else "View or change active course",
                            tint = if (isDark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = appCornerShape(18.dp),
                color = if (isDark) ExtendedTheme.colors.premiumContainer else MaterialTheme.colorScheme.secondaryContainer,
                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            ) {
                Column(Modifier.padding(appInset(16.dp)), verticalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
                    SectionLabel("YOUR CHARACTER")
                    Text("Design your learner", style = MaterialTheme.typography.titleMedium, fontWeight = appHeadingWeight())
                    Text(
                        "Choose skin, hair, eyes and nose, then adjust face shape and feature position for free.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) ExtendedTheme.colors.onPremiumContainer else MaterialTheme.colorScheme.onSecondaryContainer,
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
