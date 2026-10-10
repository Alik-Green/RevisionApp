package com.revisionapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.course.CourseFileReference
import com.revisionapp.domain.course.LearningCourse
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip

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
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (cosmeticsSelected) {
                CosmeticsStoreContent(state)
            } else {
                CourseStoreContent(
                    state = state,
                    manifestLoading = loading,
                    error = error,
                    manifestCourses = manifest?.courses.orEmpty(),
                    manifestAvailable = manifest != null,
                    catalog = catalog,
                    downloadingId = downloadingId,
                    anotherDownloadInProgress = downloadingId,
                )
            }
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
    anotherDownloadInProgress: String?,
) {
    SectionLabel("AVAILABLE COURSES")
    Text(
        "Choose a course to download it to this device. Downloaded courses are available offline; " +
            "nothing is downloaded until you choose it.",
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
        val course = catalog.course(reference.id)
        CourseStoreCard(
            reference = reference,
            downloadedCourse = course,
            isDownloading = downloadingId == reference.id,
            anotherDownloadInProgress = anotherDownloadInProgress != null && anotherDownloadInProgress != reference.id,
            onDownload = { state.downloadCourse(reference) },
            onUpdate = { state.updateCourse(reference) },
            onOpen = {
                state.selectActiveCourse(reference.id)
                state.switchTab(Route.Study)
            },
        )
    }
}

@Composable
private fun CourseStoreCard(
    reference: CourseFileReference,
    downloadedCourse: LearningCourse?,
    isDownloading: Boolean,
    anotherDownloadInProgress: Boolean,
    onDownload: () -> Unit,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reference.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (downloadedCourse != null) {
                    Text(
                        "DOWNLOADED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                reference.description.ifBlank { "A structured course with lessons and practice questions." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (downloadedCourse != null) {
                Text(
                    "${downloadedCourse.lessons.size} lessons · saved for offline study",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (downloadedCourse == null) {
                Button(
                    onClick = onDownload,
                    enabled = !isDownloading && !anotherDownloadInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (isDownloading) "Downloading…" else "Download course") }
            } else {
                OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("Study this course")
                }
                OutlinedButton(
                    onClick = onUpdate,
                    enabled = !isDownloading && !anotherDownloadInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (isDownloading) "Updating…" else "Update course") }
            }
        }
    }
}
