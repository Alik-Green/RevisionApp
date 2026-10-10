package com.revisionapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.course.CourseAnswerChecker
import com.revisionapp.domain.course.CourseQuestion
import com.revisionapp.domain.course.CourseQuestionRetryQueue
import com.revisionapp.domain.course.QuestionAnswer
import com.revisionapp.domain.progression.CharacterAppearance
import com.revisionapp.domain.progression.DailyQuestProgress
import com.revisionapp.domain.progression.LearnerProgress
import com.revisionapp.domain.progression.ProgressionRules
import com.revisionapp.platform.SoundEffect
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.CharacterAvatar
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.MathText
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.theme.ExtendedTheme

/** Player for an ordered V2 lesson. Each answer is checked using the question's authored type. */
@Composable
fun CourseLessonScreen(state: AppState, route: Route.Lesson) {
    val catalog = state.courseCatalog.collectAsState().value
    val course = catalog.course(route.courseId)
    val lesson = course?.lesson(route.lessonId)
    val questions = if (course != null && lesson != null) course.questionsFor(lesson) else emptyList()
    val retryQueue = remember(route.courseId, route.lessonId) { CourseQuestionRetryQueue(questions) }
    val questionQueue = remember(route.courseId, route.lessonId) {
        mutableStateListOf<CourseQuestion>().apply { addAll(retryQueue.snapshot()) }
    }
    val questionIndex = remember(route.courseId, route.lessonId) { mutableStateOf(0) }
    val draft = remember(route.courseId, route.lessonId, questionIndex.value) { mutableStateOf("") }
    val selectedOption = remember(route.courseId, route.lessonId, questionIndex.value) { mutableStateOf<String?>(null) }
    val answerCorrect = remember(route.courseId, route.lessonId, questionIndex.value) { mutableStateOf<Boolean?>(null) }
    val finished = remember(route.courseId, route.lessonId) { mutableStateOf(false) }
    val completionProgress = remember(route.courseId, route.lessonId) { mutableStateOf<LearnerProgress?>(null) }
    val learnerProgress = state.learnerProgress.collectAsState().value
    val initialQuestProgress = remember(route.courseId, route.lessonId) {
        ProgressionRules.dailyQuests(learnerProgress).associate { quest -> quest.id to quest.progressFraction }
    }
    val appearance = learnerProgress.characterAppearance

    if (course == null || lesson == null || questions.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader(title = "Lesson", onBack = { state.back() })
            EmptyMessage("This lesson is unavailable in the current course catalog.", Modifier.padding(horizontal = 20.dp))
        }
        return
    }

    if (finished.value) {
        LessonComplete(
            state = state,
            appearance = completionProgress.value?.characterAppearance ?: appearance,
            progress = completionProgress.value ?: learnerProgress,
            initialQuestProgress = initialQuestProgress,
        )
        return
    }

    val question = questionQueue[questionIndex.value.coerceIn(questionQueue.indices)]
    val section = course.sectionFor(lesson)
    val correct = answerCorrect.value
    val isRetry = retryQueue.isRetry(questionIndex.value)
    val questionLabel = if (isRetry) "RETRY" else "QUESTION"

    fun recordAnswer(isCorrect: Boolean) {
        state.playSoundEffect(if (isCorrect) SoundEffect.CORRECT else SoundEffect.INCORRECT)
        state.recordCourseQuestionAnswered(isCorrect)
        answerCorrect.value = isCorrect
        if (!isCorrect && retryQueue.scheduleRetry(question, questionIndex.value)) {
            questionQueue.add(question)
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = lesson.title,
            subtitle = course.name,
            onBack = { state.back() },
        )
        LessonProgressBar(questionIndex.value, questionQueue.size)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("$questionLabel ${questionIndex.value + 1} OF ${questionQueue.size}")
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
                                recordAnswer(CourseAnswerChecker.check(answer, option.id))
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
                                recordAnswer(CourseAnswerChecker.check(answer, draft.value))
                            },
                            enabled = draft.value.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Check answer") }
                    }
                }
            }

            AnimatedVisibility(
                visible = correct != null,
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 },
            ) {
                if (correct != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnswerFeedback(question, correct, appearance)
                        Button(
                            onClick = {
                                if (questionIndex.value == questionQueue.lastIndex) {
                                    state.completeCourseLesson(course.id, lesson.id)
                                    completionProgress.value = state.learnerProgress.value
                                    finished.value = true
                                } else {
                                    questionIndex.value++
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (questionIndex.value == questionQueue.lastIndex) "Finish lesson" else "Continue")
                        }
                    }
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
        answerCorrect != null && isCorrectOption -> ExtendedTheme.colors.correctContainer
        answerCorrect == false && selected -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val foreground = when {
        answerCorrect != null && isCorrectOption -> ExtendedTheme.colors.onCorrectContainer
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
private fun AnswerFeedback(
    question: CourseQuestion,
    correct: Boolean,
    appearance: CharacterAppearance,
) {
    val color = if (correct) ExtendedTheme.colors.correct else MaterialTheme.colorScheme.error
    val container = if (correct) ExtendedTheme.colors.correctContainer else MaterialTheme.colorScheme.errorContainer
    val onContainer = if (correct) ExtendedTheme.colors.onCorrectContainer else MaterialTheme.colorScheme.onErrorContainer
    Column(
        Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CharacterAvatar(appearance, size = 48.dp, celebratory = correct)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (correct) "That’s it!" else "Not quite—and that’s okay.",
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (correct) "Nice recall. Keep the idea moving." else "Use the explanation, then try it again later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
        }
        if (!correct) {
            Text(
                "Answer: ${CourseAnswerChecker.acceptedAnswerLabel(question.answer)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
            )
        }
        Text(question.explanation, style = MaterialTheme.typography.bodyMedium, color = onContainer)
    }
}

@Composable
private fun LessonProgressBar(index: Int, total: Int) {
    val fraction = if (total == 0) 0f else ((index + 1).toFloat() / total).coerceIn(0f, 1f)
    val animatedFraction = animateFloatAsState(fraction, animationSpec = tween(380), label = "lesson-progress").value
    Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (animatedFraction > 0f) {
            Box(
                Modifier.fillMaxWidth(animatedFraction)
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun LessonComplete(
    state: AppState,
    appearance: CharacterAppearance,
    progress: LearnerProgress,
    initialQuestProgress: Map<String, Float>,
) {
    val quests = ProgressionRules.dailyQuests(progress)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CharacterAvatar(appearance, size = 112.dp, celebratory = true)
        Text("Lesson complete!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionLabel("DAILY QUESTS")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (quest in quests) {
                DailyQuestProgressCard(
                    quest = quest,
                    initialFraction = initialQuestProgress[quest.id] ?: quest.progressFraction,
                )
            }
        }
        Button(onClick = { state.switchTab(Route.Study) }, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun DailyQuestProgressCard(quest: DailyQuestProgress, initialFraction: Float) {
    val animatedProgress = remember(quest.id) { Animatable(initialFraction.coerceIn(0f, 1f)) }
    LaunchedEffect(quest.id, quest.progressFraction) {
        animatedProgress.animateTo(
            targetValue = quest.progressFraction.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    quest.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${quest.current.coerceAtMost(quest.target)} / ${quest.target}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (quest.isComplete) ExtendedTheme.colors.correct else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                quest.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier.fillMaxWidth().height(9.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                if (animatedProgress.value > 0f) {
                    Box(
                        Modifier.fillMaxWidth(animatedProgress.value.coerceIn(0f, 1f)).height(9.dp)
                            .background(
                                if (quest.isComplete) ExtendedTheme.colors.correct else MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
