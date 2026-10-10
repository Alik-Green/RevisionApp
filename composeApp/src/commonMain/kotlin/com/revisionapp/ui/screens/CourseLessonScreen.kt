package com.revisionapp.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.course.CourseAnswerChecker
import com.revisionapp.domain.course.CourseQuestion
import com.revisionapp.domain.course.LearningCourse
import com.revisionapp.domain.course.QuestionAnswer
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.MathText
import com.revisionapp.ui.components.SectionLabel

/** Player for an ordered V2 lesson. Each answer is checked using the question's authored type. */
@Composable
fun CourseLessonScreen(state: AppState, route: Route.Lesson) {
    val catalog = state.courseCatalog.collectAsState().value
    val course = catalog.course(route.courseId)
    val lesson = course?.lesson(route.lessonId)
    val questions = if (course != null && lesson != null) course.questionsFor(lesson) else emptyList()
    val questionIndex = remember(route.courseId, route.lessonId) { mutableStateOf(0) }
    val draft = remember(route.courseId, route.lessonId, questionIndex.value) { mutableStateOf("") }
    val selectedOption = remember(route.courseId, route.lessonId, questionIndex.value) { mutableStateOf<String?>(null) }
    val answerCorrect = remember(route.courseId, route.lessonId, questionIndex.value) { mutableStateOf<Boolean?>(null) }
    val finished = remember(route.courseId, route.lessonId) { mutableStateOf(false) }

    if (course == null || lesson == null || questions.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader(title = "Lesson", onBack = { state.back() })
            EmptyMessage("This lesson is unavailable in the current course catalog.", Modifier.padding(horizontal = 20.dp))
        }
        return
    }

    if (finished.value) {
        LessonComplete(state, course, lesson, questions.size)
        return
    }

    val question = questions[questionIndex.value.coerceIn(questions.indices)]
    val section = course.sectionFor(lesson)
    val correct = answerCorrect.value

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = lesson.title,
            subtitle = course.name,
            onBack = { state.back() },
        )
        LessonProgressBar(questionIndex.value, questions.size)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("QUESTION ${questionIndex.value + 1} OF ${questions.size}")
                Spacer(Modifier.weight(1f))
                Text(
                    question.topicLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (section != null && section.topicIds.isNotEmpty() && question.topicId !in section.topicIds) {
                        Text(
                            "REVISIT · ${question.topicLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    MathText(question.prompt, style = MaterialTheme.typography.titleLarge)
                }
            }

            when (val answer = question.answer) {
                is QuestionAnswer.MultipleChoice -> {
                    answer.options.forEachIndexed { index, option ->
                        CourseAnswerOption(
                            number = index + 1,
                            text = option.text,
                            selected = selectedOption.value == option.id,
                            answerCorrect = correct,
                            isCorrectOption = option.id == answer.correctOptionId,
                            enabled = correct == null,
                            onClick = {
                                selectedOption.value = option.id
                                state.recordCourseQuestionAnswered()
                                answerCorrect.value = CourseAnswerChecker.check(answer, option.id)
                            },
                        )
                    }
                }

                is QuestionAnswer.TextInput -> {
                    OutlinedTextField(
                        value = draft.value,
                        onValueChange = { if (correct == null) draft.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = correct == null,
                        label = { Text("Your answer") },
                        singleLine = true,
                    )
                    if (correct == null) {
                        Button(
                            onClick = {
                                state.recordCourseQuestionAnswered()
                                answerCorrect.value = CourseAnswerChecker.check(answer, draft.value)
                            },
                            enabled = draft.value.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Check answer") }
                    }
                }
            }

            if (correct != null) {
                AnswerFeedback(question, correct)
                Button(
                    onClick = {
                        if (questionIndex.value == questions.lastIndex) {
                            state.completeCourseLesson(course.id, lesson.id)
                            finished.value = true
                        } else {
                            questionIndex.value++
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (questionIndex.value == questions.lastIndex) "Finish lesson" else "Continue")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CourseAnswerOption(
    number: Int,
    text: String,
    selected: Boolean,
    answerCorrect: Boolean?,
    isCorrectOption: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container = when {
        answerCorrect != null && isCorrectOption -> MaterialTheme.colorScheme.tertiaryContainer
        answerCorrect == false && selected -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val foreground = when {
        answerCorrect != null && isCorrectOption -> MaterialTheme.colorScheme.onTertiaryContainer
        answerCorrect == false && selected -> MaterialTheme.colorScheme.onErrorContainer
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = container,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$number", color = foreground, fontWeight = FontWeight.Bold)
            MathText(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = foreground)
            if (answerCorrect != null && isCorrectOption) {
                Icon(Icons.Filled.Check, contentDescription = "Correct answer", tint = foreground)
            } else if (answerCorrect == false && selected) {
                Icon(Icons.Filled.Close, contentDescription = "Your answer", tint = foreground)
            }
        }
    }
}

@Composable
private fun AnswerFeedback(question: CourseQuestion, correct: Boolean) {
    val color = if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Column(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .border(1.dp, color, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (correct) "Correct!" else "Not quite",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        if (!correct) {
            Text(
                "Answer: ${CourseAnswerChecker.acceptedAnswerLabel(question.answer)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(question.explanation, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LessonProgressBar(index: Int, total: Int) {
    val fraction = if (total == 0) 0f else ((index + 1).toFloat() / total).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun LessonComplete(state: AppState, course: LearningCourse, lesson: CourseLesson, questionCount: Int) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(title = "Lesson complete", subtitle = course.name)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎉", style = MaterialTheme.typography.displayMedium)
            Text(lesson.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "$questionCount questions answered. Your path progress is saved.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Daily quest coins are added automatically when you reach a goal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { state.switchTab(Route.Study) }, modifier = Modifier.fillMaxWidth()) {
                Text("Back to your path")
            }
            val orderedLessons = course.orderedLessons()
            val hasNextLesson = orderedLessons.indexOfFirst { it.id == lesson.id } in 0 until orderedLessons.lastIndex
            Text(
                if (hasNextLesson) "Next lesson unlocked" else "Course path complete",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
