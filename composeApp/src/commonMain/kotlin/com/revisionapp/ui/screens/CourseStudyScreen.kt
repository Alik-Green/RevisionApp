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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.course.CourseLesson
import com.revisionapp.domain.course.CourseSection
import com.revisionapp.domain.course.LearningCourse
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel

/** Mobile-first course path. Only the selected course's ordered lesson trail is shown. */
@Composable
fun CourseStudyScreen(state: AppState) {
    val catalog = state.courseCatalog.collectAsState().value
    val loading = state.courseCatalogLoading.collectAsState().value
    val loadError = state.courseCatalogError.collectAsState().value
    val progress = state.learnerProgress.collectAsState().value
    val progressionReady = state.progressionReady.collectAsState().value
    val course = catalog.course(progress.activeCourseId) ?: catalog.courses.firstOrNull()
    val menuExpanded = remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Study",
            subtitle = course?.name ?: "Your learning path",
            trailing = {
                if (catalog.courses.size > 1) {
                    Box {
                        TextButton(onClick = { menuExpanded.value = true }) {
                            Text("Change course")
                        }
                        DropdownMenu(
                            expanded = menuExpanded.value,
                            onDismissRequest = { menuExpanded.value = false },
                        ) {
                            for (option in catalog.courses) {
                                DropdownMenuItem(
                                    text = { Text(option.name) },
                                    onClick = {
                                        state.selectActiveCourse(option.id)
                                        menuExpanded.value = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )

        if (loading || !progressionReady) {
            EmptyMessage("Getting your course path ready…", Modifier.padding(horizontal = 20.dp))
            return@Column
        }
        if (course == null) {
            EmptyMessage(
                loadError ?: "No V2 course is bundled yet.",
                Modifier.padding(horizontal = 20.dp),
            )
            return@Column
        }

        val orderedLessons = course.orderedLessons()
        val completedCount = orderedLessons.count { state.isCourseLessonComplete(course.id, it.id) }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CourseSummaryCard(course, completedCount, orderedLessons.size)
            SectionLabel("YOUR PATH")
            if (course.sections.isEmpty()) {
                EmptyMessage("This course has no lesson sections yet.")
            }
            for ((sectionIndex, section) in course.sections.withIndex()) {
                val unlocked = course.sections.take(sectionIndex)
                    .flatMap { it.lessonIds }
                    .all { state.isCourseLessonComplete(course.id, it) }
                if (unlocked) {
                    CoursePathSection(state, course, section, orderedLessons)
                } else {
                    LockedCourseSection(section)
                }
            }
            if (!loadError.isNullOrBlank()) {
                EmptyMessage("Some bundled course content could not be read: $loadError")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CourseSummaryCard(course: LearningCourse, completed: Int, total: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(course.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(course.description, style = MaterialTheme.typography.bodyMedium)
        Text(
            "$completed of $total lessons complete",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        PathProgressBar(completed, total, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LockedCourseSection(section: CourseSection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = "Topic locked", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
) {
    val sectionLessons = section.lessonIds.mapNotNull(course::lesson)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            section.topicLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
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
                number = globalIndex + 1,
                completed = completed,
                current = current,
                unlocked = unlocked,
                showConnector = sectionIndex != sectionLessons.lastIndex,
            )
        }
    }
}

@Composable
private fun LessonPathNode(
    state: AppState,
    course: LearningCourse,
    lesson: CourseLesson,
    number: Int,
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
    val cardColor = when {
        current -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(54.dp).background(nodeColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    completed -> Icon(Icons.Filled.Check, contentDescription = "Completed", tint = nodeContent)
                    !unlocked -> Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = nodeContent)
                    current -> Icon(Icons.Filled.PlayArrow, contentDescription = "Start lesson", tint = nodeContent)
                    else -> Text(number.toString(), color = nodeContent, fontWeight = FontWeight.Bold)
                }
            }
            if (showConnector) {
                Box(
                    Modifier.padding(top = 4.dp).width(3.dp).height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .clickable(enabled = unlocked) { state.openCourseLesson(course.id, lesson.id) },
            shape = RoundedCornerShape(16.dp),
            color = cardColor,
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lesson.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            completed -> "DONE"
                            current -> "START"
                            unlocked -> "REPLAY"
                            else -> "LOCKED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (lesson.description.isNotBlank()) {
                    Text(
                        lesson.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${lesson.questionIds.size} questions",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PathProgressBar(completed: Int, total: Int, color: Color) {
    val fraction = if (total == 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    ) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(color, RoundedCornerShape(4.dp)))
        }
    }
}
