package com.revisionapp.ui.screens

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

/** A repository-backed catalogue. Only tapping Download fetches the course JSON. */
@Composable
fun CourseStoreScreen(state: AppState) {
    val manifest = state.courseStoreManifest.collectAsState().value
    val loading = state.courseStoreLoading.collectAsState().value
    val error = state.courseStoreError.collectAsState().value
    val catalog = state.courseCatalog.collectAsState().value
    val downloadingId = state.courseDownloadId.collectAsState().value

    LaunchedEffect(Unit) { state.refreshCourseStore() }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Course Store",
            subtitle = "Courses from the RevisionApp repository",
            onBack = { state.back() },
            trailing = {
                TextButton(onClick = { state.refreshCourseStore() }, enabled = !loading) {
                    Text(if (loading) "Loading…" else "Refresh")
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel("AVAILABLE COURSES")
            Text(
                "Choose a course to download it to this device. Downloaded courses are available offline; " +
                    "nothing is downloaded until you choose it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (loading && manifest == null) {
                EmptyMessage("Loading the course list from the repository…")
            }
            if (!error.isNullOrBlank()) {
                EmptyMessage("Couldn't load the course list. Check your connection and try again. $error")
                OutlinedButton(
                    onClick = { state.refreshCourseStore() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Try again") }
            }
            val references = manifest?.courses.orEmpty()
            if (manifest != null && references.isEmpty()) {
                EmptyMessage("There are no courses in the repository yet.")
            }
            for (reference in references) {
                val course = catalog.course(reference.id)
                CourseStoreCard(
                    reference = reference,
                    downloadedCourse = course,
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
