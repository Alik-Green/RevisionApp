package com.revisionapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.course.CourseFileReference
import com.revisionapp.domain.course.LearningCourse
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppButton as Button
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.AppOutlinedButton as OutlinedButton
import com.revisionapp.ui.components.AppTextButton as TextButton
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip
import com.revisionapp.ui.theme.appCornerShape
import com.revisionapp.ui.theme.appInset

/** Primary Store tab with separate course-download and avatar-cosmetic shelves. */
@Composable
fun CourseStoreScreen(state: AppState) {
    val manifest by state.courseStoreManifest.collectAsState()
    val loading by state.courseStoreLoading.collectAsState()
    val error by state.courseStoreError.collectAsState()
    val catalog by state.courseCatalog.collectAsState()
    val downloadingId by state.courseDownloadId.collectAsState()
    val cosmeticsSelected by state.cosmeticsStoreSelected.collectAsState()

    LaunchedEffect(cosmeticsSelected) {
        if (!cosmeticsSelected) state.refreshCourseStore()
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Store",
            subtitle = "Courses to study · cosmetics to collect",
            trailing = {
                if (!cosmeticsSelected) {
                    TextButton(onClick = { state.refreshCourseStore() }, enabled = !loading) {
                        Text(if (loading) "Loading…" else "Refresh")
                    }
                }
            },
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = appInset(16.dp)),
            horizontalArrangement = Arrangement.spacedBy(appInset(8.dp)),
        ) {
            ToggleChip(
                label = "📚 Courses",
                selected = !cosmeticsSelected,
                onClick = { state.selectStoreShelf(cosmetics = false) },
            )
            ToggleChip(
                label = "✨ Cosmetics",
                selected = cosmeticsSelected,
                onClick = { state.selectStoreShelf(cosmetics = true) },
            )
        }
        if (cosmeticsSelected) {
            CosmeticsStoreContent(state, Modifier.weight(1f))
        } else {
            CourseStoreContent(
                state = state,
                manifestLoading = loading,
                error = error,
                manifestCourses = manifest?.courses.orEmpty(),
                manifestAvailable = manifest != null,
                catalog = catalog,
                downloadingId = downloadingId,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CourseStoreContent(
    state: AppState,
    manifestLoading: Boolean,
    error: String?,
    manifestCourses: List<CourseFileReference>,
    manifestAvailable: Boolean,
    catalog: com.revisionapp.domain.course.CourseCatalog,
    downloadingId: String?,
    modifier: Modifier = Modifier,
) {
    val selectedCourseId = remember { mutableStateOf<String?>(null) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = appInset(16.dp), vertical = appInset(12.dp)),
        verticalArrangement = Arrangement.spacedBy(appInset(11.dp)),
    ) {
        SectionLabel("AVAILABLE COURSES")
        Text(
            "Browse a course, open its details, then choose Download. Downloads stay opt-in and are saved for offline study.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (manifestLoading && !manifestAvailable) {
            EmptyMessage("Loading the course list from the repository…")
        }
        if (!error.isNullOrBlank()) {
            EmptyMessage("Couldn't load the course list. Check your connection and try again. $error")
            OutlinedButton(
                onClick = { state.refreshCourseStore() },
                enabled = !manifestLoading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Try again") }
        }
        if (manifestAvailable && manifestCourses.isEmpty()) {
            EmptyMessage("There are no courses in the repository yet.")
        }
        for (reference in manifestCourses) {
            val downloadedCourse = catalog.course(reference.id)
            val selected = selectedCourseId.value == reference.id
            CourseStoreRow(
                reference = reference,
                downloadedCourse = downloadedCourse,
                selected = selected,
                onClick = { selectedCourseId.value = if (selected) null else reference.id },
            )
            if (selected) {
                CourseDetailCard(
                    reference = reference,
                    downloadedCourse = downloadedCourse,
                    isDownloading = downloadingId == reference.id,
                    anotherDownloadInProgress = downloadingId != null && downloadingId != reference.id,
                    onDownload = { state.downloadCourse(reference) },
                    onUpdate = { state.updateCourse(reference) },
                    onOpen = {
                        state.selectActiveCourse(reference.id)
                        state.switchTab(Route.Study)
                    },
                )
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun CourseStoreRow(
    reference: CourseFileReference,
    downloadedCourse: LearningCourse?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = appCornerShape(19.dp),
        color = when {
            selected && isDark -> MaterialTheme.colorScheme.tertiaryContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected && isDark) MaterialTheme.colorScheme.tertiary
            else if (selected) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        shadowElevation = if (isDark && selected) 6.dp else if (isDark) 3.dp else 0.dp,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(appInset(14.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(appInset(13.dp)),
        ) {
            CourseGlyph(reference, size = 58.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appInset(3.dp))) {
                Text(reference.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (downloadedCourse != null) {
                        "Downloaded · ${downloadedCourse.lessons.size} lessons"
                    } else {
                        "Available to download"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (downloadedCourse != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (selected) "⌃" else "›",
                style = MaterialTheme.typography.headlineSmall,
                color = if (selected && isDark) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CourseDetailCard(
    reference: CourseFileReference,
    downloadedCourse: LearningCourse?,
    isDownloading: Boolean,
    anotherDownloadInProgress: Boolean,
    onDownload: () -> Unit,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = appCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
        shadowElevation = if (isDark) 4.dp else 0.dp,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(appInset(17.dp)), verticalArrangement = Arrangement.spacedBy(appInset(11.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(appInset(12.dp))) {
                CourseGlyph(reference, size = 64.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appInset(2.dp))) {
                    SectionLabel(if (downloadedCourse == null) "COURSE DETAILS" else "DOWNLOADED COURSE")
                    Text(reference.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                reference.description.ifBlank {
                    downloadedCourse?.description ?: "A structured course with lessons and practice questions."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (downloadedCourse != null) {
                Text(
                    "${downloadedCourse.lessons.size} lessons · saved for offline study",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Text("Choose Download to save this course on your device.", style = MaterialTheme.typography.labelMedium)
            }
            if (downloadedCourse == null) {
                Button(
                    onClick = onDownload,
                    enabled = !isDownloading && !anotherDownloadInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (isDownloading) "Downloading…" else "Download course") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(appInset(9.dp))) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open course") }
                    OutlinedButton(
                        onClick = onUpdate,
                        enabled = !isDownloading && !anotherDownloadInProgress,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (isDownloading) "Updating…" else "Update") }
                }
            }
        }
    }
}

@Composable
private fun CourseGlyph(reference: CourseFileReference, size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.36f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(courseGlyph(reference), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

private fun courseGlyph(reference: CourseFileReference): String {
    val key = "${reference.id} ${reference.name}".lowercase()
    return when {
        "tmua" in key || "admission" in key -> "🧠"
        "math" in key || "algebra" in key || "calculus" in key -> "∑"
        "physics" in key || "science" in key -> "⚛"
        "computer" in key || "coding" in key -> "⌘"
        else -> "📘"
    }
}
