package com.revisionapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.course.CourseLesson
import com.revisionapp.domain.course.CourseSection
import com.revisionapp.domain.course.LearningCourse
import com.revisionapp.ui.AppState
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.theme.DarkAccentPalette
import com.revisionapp.ui.theme.appCornerShape
import com.revisionapp.ui.theme.appInset
import kotlin.math.sin
import com.revisionapp.ui.components.AppTextButton as TextButton

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
            EmptyMessage("Getting your course path ready…", Modifier.padding(horizontal = appInset(20.dp)))
            return@Column
        }
        if (course == null) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = appInset(20.dp), vertical = appInset(18.dp)),
                verticalArrangement = Arrangement.spacedBy(appInset(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = appCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(appInset(18.dp)), verticalArrangement = Arrangement.spacedBy(appInset(6.dp))) {
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
                .padding(horizontal = appInset(16.dp), vertical = appInset(9.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(14.dp)),
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
                CoursePathSection(
                    state = state,
                    course = course,
                    section = section,
                    sectionIndex = sectionIndex,
                    orderedLessons = orderedLessons,
                    modifier = Modifier.widthIn(max = 660.dp).fillMaxWidth(),
                )
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        modifier = modifier,
        shape = appCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f)),
    ) {
        Row(
            Modifier.padding(horizontal = appInset(13.dp), vertical = appInset(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(appInset(11.dp)),
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appInset(2.dp))) {
                SectionLabel("ACTIVE COURSE")
                Text(course.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "$completed of $total lessons complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            IconButton(onClick = onInfo) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "Course details and switching",
                    tint = if (isDark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                verticalArrangement = Arrangement.spacedBy(appInset(10.dp)),
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
private fun CoursePathSection(
    state: AppState,
    course: LearningCourse,
    section: CourseSection,
    sectionIndex: Int,
    orderedLessons: List<CourseLesson>,
    modifier: Modifier = Modifier,
) {
    val sectionLessons = section.lessonIds.mapNotNull(course::lesson)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val topicColour = coursePathColour(sectionIndex, isDark)
    val topicTextColour = if (isDark && topicColour == DarkAccentPalette.HumpbackBlue) {
        DarkAccentPalette.PrimaryText
    } else {
        topicColour
    }
    val topicLabel = section.topicLabel.ifBlank { section.title }

    Surface(
        modifier = modifier,
        shape = appCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, topicColour.copy(alpha = if (isDark) 0.48f else 0.38f)),
    ) {
        Column(
            Modifier.padding(horizontal = appInset(16.dp), vertical = appInset(14.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(4.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (section.title.isNotBlank() && section.title != topicLabel) {
                SectionLabel(section.title.uppercase())
            }
            Text(
                topicLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = topicTextColour,
            )
            Spacer(Modifier.height(5.dp))
            CoursePathNodes(
                state = state,
                course = course,
                section = section,
                sectionLessons = sectionLessons,
                orderedLessons = orderedLessons,
                topicLabel = topicLabel,
                topicColour = topicColour,
                isDark = isDark,
            )
        }
    }
}

@Composable
private fun CoursePathNodes(
    state: AppState,
    course: LearningCourse,
    section: CourseSection,
    sectionLessons: List<CourseLesson>,
    orderedLessons: List<CourseLesson>,
    topicLabel: String,
    topicColour: Color,
    isDark: Boolean,
) {
    val nodeSize = 56.dp
    val nodeSpacing = if (isDark) 78.dp else 84.dp
    val topInset = if (isDark) 6.dp else 8.dp
    val pathHeight = if (sectionLessons.isEmpty()) {
        12.dp
    } else {
        nodeSize + topInset + topInset + nodeSpacing * (sectionLessons.size - 1).toFloat()
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(pathHeight)) {
        val amplitude = minOf(maxWidth * 0.085f, 30.dp)
        val offsets = remember(sectionLessons.size, maxWidth) {
            sectionLessons.indices.map { index -> amplitude * sin(index.toDouble() * 1.32).toFloat() }
        }
        val completedPathSteps = sectionLessons.dropLast(1).map { lesson ->
            state.isCourseLessonComplete(course.id, lesson.id)
        }

        Canvas(Modifier.matchParentSize()) {
            if (sectionLessons.size > 1) {
                val nodeRadius = nodeSize.toPx() / 2f
                val step = nodeSpacing.toPx()
                val firstY = topInset.toPx() + nodeRadius
                fun x(index: Int): Float = size.width / 2f + offsets[index].toPx()
                for (index in 0 until sectionLessons.lastIndex) {
                    val startX = x(index)
                    val endX = x(index + 1)
                    val startY = firstY + step * index.toFloat()
                    val endY = firstY + step * (index + 1).toFloat()
                    val middleY = (startY + endY) / 2f
                    val segment = Path().apply {
                        moveTo(startX, startY)
                        cubicTo(startX, middleY, endX, middleY, endX, endY)
                    }
                    drawPath(
                        path = segment,
                        color = if (isDark && completedPathSteps[index]) {
                            DarkAccentPalette.FeatherGreen
                        } else {
                            topicColour.copy(alpha = if (isDark) 0.62f else 0.48f)
                        },
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }

        for ((index, lesson) in sectionLessons.withIndex()) {
            val globalIndex = orderedLessons.indexOfFirst { it.id == lesson.id }
            val completed = state.isCourseLessonComplete(course.id, lesson.id)
            val unlocked = state.canOpenCourseLesson(course.id, lesson.id)
            val current = unlocked && !completed && globalIndex >= 0 && orderedLessons.take(globalIndex).all {
                state.isCourseLessonComplete(course.id, it.id)
            }
            val review = unlocked && isCuratedReviewLesson(course, section, lesson)
            val description = when {
                !unlocked -> "Locked $topicLabel lesson ${index + 1}"
                current -> "Continue with $topicLabel, lesson ${index + 1}"
                review -> "Curated review lesson ${index + 1} in $topicLabel"
                completed -> "Completed lesson ${index + 1} in $topicLabel"
                else -> "Lesson ${index + 1} in $topicLabel"
            }
            val nodeAccent = when {
                isDark && (current || completed) -> DarkAccentPalette.FeatherGreen
                isDark && review -> DarkAccentPalette.HumpbackBlue
                else -> topicColour
            }
            val fillColour = if (unlocked) {
                nodeAccent
            } else {
                topicColour.copy(alpha = if (isDark) 0.25f else 0.18f)
            }
            val nodeContentColour = when {
                !isDark -> Color.White
                nodeAccent == DarkAccentPalette.HumpbackBlue -> DarkAccentPalette.PrimaryText
                else -> DarkAccentPalette.Background
            }
            val borderColour = when {
                current -> DarkAccentPalette.MaskGreen.takeIf { isDark } ?: nodeContentColour
                completed && isDark -> DarkAccentPalette.FeatherGreen
                review && isDark -> DarkAccentPalette.MacawBlue
                unlocked -> topicColour
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            val nodeBorderWidth = when {
                current -> 3.dp
                completed && isDark -> 2.dp
                review && isDark -> 2.dp
                else -> 1.dp
            }
            val nodeDepth = when {
                !isDark && current -> 5.dp
                !isDark -> 1.dp
                !unlocked -> 0.dp
                current -> 8.dp
                completed -> 6.dp
                else -> 4.dp
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = offsets[index], y = topInset + nodeSpacing * index.toFloat())
                    .size(nodeSize)
                    .semantics { contentDescription = description }
                    .clickable(enabled = unlocked, role = Role.Button) {
                        state.openCourseLesson(course.id, lesson.id)
                    },
                shape = CircleShape,
                color = fillColour,
                border = BorderStroke(nodeBorderWidth, borderColour),
                shadowElevation = nodeDepth,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        !unlocked -> Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        current -> Icon(Icons.Filled.Star, contentDescription = null, tint = nodeContentColour)
                        review -> Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = nodeContentColour)
                        completed -> Icon(Icons.Filled.Check, contentDescription = null, tint = nodeContentColour)
                        else -> Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = nodeContentColour,
                        )
                    }
                }
            }
        }
    }
}

private fun isCuratedReviewLesson(
    course: LearningCourse,
    section: CourseSection,
    lesson: CourseLesson,
): Boolean {
    if (lesson.isCuratedReview) return true
    if (section.topicIds.isEmpty()) return false
    val currentTopicIds = section.topicIds.toSet()
    return course.questionsFor(lesson).any { question -> question.topicId !in currentTopicIds }
}

private val LIGHT_COURSE_PATH_COLOURS = listOf(
    Color(0xFF4B35B5),
    Color(0xFF087B71),
    Color(0xFFA5145B),
    Color(0xFF1F63B6),
    Color(0xFF4A7528),
    Color(0xFFAB4F10),
)

private val DARK_COURSE_PATH_COLOURS = listOf(
    DarkAccentPalette.MaskGreen,
    DarkAccentPalette.MacawBlue,
    DarkAccentPalette.HumpbackBlue,
    DarkAccentPalette.BeeYellow,
    DarkAccentPalette.FoxOrange,
    DarkAccentPalette.BeetlePurple,
)

private fun coursePathColour(sectionIndex: Int, isDark: Boolean): Color {
    val palette = if (isDark) DARK_COURSE_PATH_COLOURS else LIGHT_COURSE_PATH_COLOURS
    return palette[sectionIndex % palette.size]
}
