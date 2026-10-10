package com.revisionapp.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.course.CourseLesson
import com.revisionapp.domain.course.CourseSection
import com.revisionapp.domain.course.LearningCourse
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel

/** Course-first study home with a compact switch/info card and a centered topic path. */
@Composable
fun CourseStudyScreen(state: AppState) {
    val catalog = state.courseCatalog.collectAsState().value
    val loading = state.courseCatalogLoading.collectAsState().value
    val loadError = state.courseCatalogError.collectAsState().value
    val progress = state.learnerProgress.collectAsState().value
    val progressionReady = state.progressionReady.collectAsState().value
    val course = catalog.course(progress.activeCourseId) ?: catalog.courses.firstOrNull()
    var showCourseInfo by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Study",
            subtitle = course?.name ?: "Your learning path",
        )

        if (loading || !progressionReady) {
            EmptyMessage("Getting your course path ready…", Modifier.padding(horizontal = 20.dp))
            return@Column
        }
        if (course == null) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("ACTIVE COURSE")
                        Text("No course downloaded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Choose a course from the Store tab to begin. Course downloads are always opt-in.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                if (!loadError.isNullOrBlank()) {
                    EmptyMessage("Saved course data needs attention. Open the Store tab to retry. $loadError")
                }
            }
            return@Column
        }

        val orderedLessons = course.orderedLessons()
        val completedCount = orderedLessons.count { state.isCourseLessonComplete(course.id, it.id) }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActiveCourseCard(
                course = course,
                completed = completedCount,
                total = orderedLessons.size,
                onInfo = { showCourseInfo = true },
                modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth(),
            )
            SectionLabel("YOUR TOPIC PATH")
            if (course.sections.isEmpty()) {
                EmptyMessage("This course has no lesson sections yet.")
            }
            for ((sectionIndex, section) in course.sections.withIndex()) {
                val unlocked = course.sections.take(sectionIndex)
                    .flatMap { it.lessonIds }
                    .all { state.isCourseLessonComplete(course.id, it) }
                if (unlocked) {
                    CoursePathSection(
                        state = state,
                        course = course,
                        section = section,
                        orderedLessons = orderedLessons,
                        modifier = Modifier.widthIn(max = 660.dp).fillMaxWidth(),
                    )
                } else {
                    LockedCourseSection(
                        section = section,
                        modifier = Modifier.widthIn(max = 660.dp).fillMaxWidth(),
                    )
                }
            }
            if (!loadError.isNullOrBlank()) {
                EmptyMessage("Saved course data needs to be downloaded again. Open the Store tab to retry. $loadError")
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showCourseInfo && course != null) {
        CourseInfoDialog(
            course = course,
            catalog = catalog,
            completed = completedCountFor(state, course),
            onClose = { showCourseInfo = false },
            onSelectCourse = state::selectActiveCourse,
        )
    }
}

@Composable
private fun ActiveCourseCard(
    course: LearningCourse,
    completed: Int,
    total: Int,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f)),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📘", style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel("ACTIVE COURSE")
                Text(course.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "$completed of $total lessons complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            IconButton(onClick = onInfo) {
                Icon(Icons.Filled.Info, contentDescription = "Course details and switching")
            }
        }
    }
}

@Composable
private fun CourseInfoDialog(
    course: LearningCourse,
    catalog: com.revisionapp.domain.course.CourseCatalog,
    completed: Int,
    onClose: () -> Unit,
    onSelectCourse: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(course.name) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(course.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${course.orderedLessons().size} lessons · $completed complete",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (catalog.courses.size > 1) {
                    HorizontalDivider()
                    Text("SWITCH DOWNLOADED COURSE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    for (option in catalog.courses) {
                        TextButton(
                            onClick = { onSelectCourse(option.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (option.id == course.id) "✓  ${option.name} · Active" else option.name,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

private fun completedCountFor(state: AppState, course: LearningCourse): Int =
    course.orderedLessons().count { state.isCourseLessonComplete(course.id, it.id) }

@Composable
private fun LockedCourseSection(section: CourseSection, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = "Topic locked", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(section.topicLabel.ifBlank { section.title }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Complete the lessons above to unlock this topic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CoursePathSection(
    state: AppState,
    course: LearningCourse,
    section: CourseSection,
    orderedLessons: List<CourseLesson>,
    modifier: Modifier = Modifier,
) {
    val sectionLessons = section.lessonIds.mapNotNull(course::lesson)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel(section.title.uppercase())
            Text(
                section.topicLabel.ifBlank { section.title },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${sectionLessons.size} practice ${if (sectionLessons.size == 1) "session" else "sessions"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            for ((sectionIndex, lesson) in sectionLessons.withIndex()) {
                val globalIndex = orderedLessons.indexOfFirst { it.id == lesson.id }
                val completed = state.isCourseLessonComplete(course.id, lesson.id)
                val unlocked = state.canOpenCourseLesson(course.id, lesson.id)
                val current = unlocked && !completed && orderedLessons.take(globalIndex).none {
                    !state.isCourseLessonComplete(course.id, it.id)
                }
                LessonPathNode(
                    state = state,
                    course = course,
                    lesson = lesson,
                    topicLabel = section.topicLabel.ifBlank { section.title },
                    practiceNumber = sectionIndex + 1,
                    completed = completed,
                    current = current,
                    unlocked = unlocked,
                    showConnector = sectionIndex != sectionLessons.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun LessonPathNode(
    state: AppState,
    course: LearningCourse,
    lesson: CourseLesson,
    topicLabel: String,
    practiceNumber: Int,
    completed: Boolean,
    current: Boolean,
    unlocked: Boolean,
    showConnector: Boolean,
) {
    val nodeColor = when {
        completed -> MaterialTheme.colorScheme.tertiary
        current -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val nodeContent = when {
        completed -> MaterialTheme.colorScheme.onTertiary
        current -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        completed -> "Review practice"
        current -> "New topic"
        unlocked -> "Practice"
        else -> "Coming up"
    }
    val borderColor = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(46.dp).background(nodeColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    current -> Icon(Icons.Filled.Star, contentDescription = "New topic", tint = nodeContent)
                    completed || unlocked -> Icon(Icons.Filled.FitnessCenter, contentDescription = "Review or practice", tint = nodeContent)
                    else -> Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = nodeContent)
                }
            }
            if (showConnector) {
                Box(
                    Modifier.padding(top = 4.dp).width(3.dp).height(20.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(top = 1.dp)
                .border(if (current) 2.dp else 1.dp, borderColor, RoundedCornerShape(15.dp))
                .clickable(enabled = unlocked) { state.openCourseLesson(course.id, lesson.id) },
            shape = RoundedCornerShape(15.dp),
            color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Row(
                Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(topicLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Practice $practiceNumber · ${lesson.questionIds.size} questions", style = MaterialTheme.typography.labelSmall)
                }
                if (completed) {
                    Text("DONE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                } else if (current) {
                    Button(onClick = { state.openCourseLesson(course.id, lesson.id) }) { Text("Start") }
                }
            }
        }
    }
}
