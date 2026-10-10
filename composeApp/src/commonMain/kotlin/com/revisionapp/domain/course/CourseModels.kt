package com.revisionapp.domain.course

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** V2 content is authored as a course path, ordered lessons, and reusable questions. */
@Serializable
data class CourseCatalogManifest(
    val schemaVersion: Int,
    val courses: List<CourseFileReference> = emptyList(),
)

@Serializable
data class CourseFileReference(
    val id: String,
    val name: String,
    val file: String,
    val description: String = "",
)

@Serializable
data class CourseCatalog(val schemaVersion: Int, val courses: List<LearningCourse>) {
    fun course(id: String): LearningCourse? = courses.firstOrNull { it.id == id }

    fun validationErrors(): List<String> = buildList {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) add("Unsupported course schema version $schemaVersion")
        if (courses.map { it.id }.distinct().size != courses.size) add("Course ids must be unique")
        courses.forEach { course ->
            course.validationErrors().forEach { add("${course.id}: $it") }
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}

/** A named course with topic sections and an ordered path through lessons. */
@Serializable
data class LearningCourse(
    val id: String,
    val name: String,
    val description: String,
    val sections: List<CourseSection>,
    val lessons: List<CourseLesson>,
    val questions: List<CourseQuestion>,
) {
    private val lessonById: Map<String, CourseLesson> get() = lessons.associateBy { it.id }
    private val questionById: Map<String, CourseQuestion> get() = questions.associateBy { it.id }

    /** Flattened in path order, not file order. */
    fun orderedLessons(): List<CourseLesson> = sections.flatMap { section ->
        section.lessonIds.mapNotNull(lessonById::get)
    }

    fun lesson(id: String): CourseLesson? = lessonById[id]

    fun question(id: String): CourseQuestion? = questionById[id]

    fun questionsFor(lesson: CourseLesson): List<CourseQuestion> = lesson.questionIds.mapNotNull(questionById::get)

    fun sectionFor(lesson: CourseLesson): CourseSection? = sections.firstOrNull { it.id == lesson.sectionId }

    fun validationErrors(): List<String> = buildList {
        if (id.isBlank()) add("Course id is blank")
        if (sections.map { it.id }.distinct().size != sections.size) add("Section ids must be unique")
        if (lessons.map { it.id }.distinct().size != lessons.size) add("Lesson ids must be unique")
        if (questions.map { it.id }.distinct().size != questions.size) add("Question ids must be unique")

        val sectionIds = sections.map { it.id }.toSet()
        val lessonIds = lessons.map { it.id }.toSet()
        val questionIds = questions.map { it.id }.toSet()
        val pathLessonIds = sections.flatMap { it.lessonIds }
        if (pathLessonIds.distinct().size != pathLessonIds.size) add("A lesson may appear only once in a course path")
        if (pathLessonIds.toSet() != lessonIds) add("Every lesson must appear in exactly one section path")

        sections.forEach { section ->
            if (section.lessonIds.any { it !in lessonIds }) add("Section '${section.id}' references an unknown lesson")
        }
        lessons.forEach { lesson ->
            if (lesson.sectionId !in sectionIds) add("Lesson '${lesson.id}' references an unknown section")
            if (lesson.questionIds.isEmpty()) add("Lesson '${lesson.id}' must contain at least one question")
            if (lesson.questionIds.any { it !in questionIds }) add("Lesson '${lesson.id}' references an unknown question")
        }
        questions.forEach { question ->
            when (val answer = question.answer) {
                is QuestionAnswer.MultipleChoice -> {
                    val optionIds = answer.options.map { it.id }
                    if (answer.options.size < 2) add("Question '${question.id}' needs at least two options")
                    if (optionIds.distinct().size != optionIds.size) add("Question '${question.id}' has duplicate option ids")
                    if (answer.correctOptionId !in optionIds) add("Question '${question.id}' has no matching correct option")
                }

                is QuestionAnswer.TextInput -> if (answer.acceptedAnswers.none { it.isNotBlank() }) {
                    add("Question '${question.id}' needs an accepted text answer")
                }
            }
        }
    }
}

/** A topic-shaped segment of the learner's path. Lessons can refer across sections. */
@Serializable
data class CourseSection(
    val id: String,
    val title: String,
    val topicLabel: String,
    val topicIds: List<String> = emptyList(),
    val lessonIds: List<String>,
)

/** An ordered sequence of reusable question ids. */
@Serializable
data class CourseLesson(
    val id: String,
    val title: String,
    val sectionId: String,
    val questionIds: List<String>,
    val description: String = "",
)

@Serializable
data class CourseQuestion(
    val id: String,
    val topicId: String,
    val topicLabel: String,
    val prompt: String,
    val answer: QuestionAnswer,
    val explanation: String,
    val answerInstruction: String = "",
)

/** A lesson-local queue that appends each initially missed question exactly once. */
class CourseQuestionRetryQueue(initialQuestions: List<CourseQuestion>) {
    private val initialQuestionCount = initialQuestions.size
    private val questions = initialQuestions.toMutableList()
    private val retriedQuestionIndices = mutableSetOf<Int>()

    fun snapshot(): List<CourseQuestion> = questions.toList()

    fun isRetry(queueIndex: Int): Boolean = queueIndex >= initialQuestionCount

    /** Returns true only when this initial question occurrence is appended once. */
    fun scheduleRetry(question: CourseQuestion, queueIndex: Int): Boolean {
        if (queueIndex !in 0 until initialQuestionCount || questions[queueIndex] != question) return false
        if (!retriedQuestionIndices.add(queueIndex)) return false
        questions.add(question)
        return true
    }
}

@Serializable
sealed interface QuestionAnswer {
    @Serializable
    @SerialName("multiple_choice")
    data class MultipleChoice(
        val options: List<CourseOption>,
        val correctOptionId: String,
    ) : QuestionAnswer

    @Serializable
    @SerialName("text_input")
    data class TextInput(
        val acceptedAnswers: List<String>,
    ) : QuestionAnswer
}

@Serializable
data class CourseOption(val id: String, val text: String)

/** The small first V2 pilot uses exact normalized text matching and authored choices. */
object CourseAnswerChecker {
    fun check(answer: QuestionAnswer, input: String): Boolean = when (answer) {
        is QuestionAnswer.MultipleChoice -> input == answer.correctOptionId
        is QuestionAnswer.TextInput -> {
            val normalized = normalize(input)
            normalized.isNotEmpty() && answer.acceptedAnswers.any { normalize(it) == normalized }
        }
    }

    fun acceptedAnswerLabel(answer: QuestionAnswer): String = when (answer) {
        is QuestionAnswer.MultipleChoice -> answer.options.firstOrNull { it.id == answer.correctOptionId }?.text.orEmpty()
        is QuestionAnswer.TextInput -> answer.acceptedAnswers.firstOrNull().orEmpty()
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .trim { it.isWhitespace() || it in EDGE_PUNCTUATION }
            .replace(WHITESPACE, " ")
            .replace(MATH_OPERATOR_SPACING) { match -> match.groupValues[1] }
            .replace(COUNTER_EXAMPLE) { match ->
                "counterexample" + if (match.value.endsWith("s")) "s" else ""
            }

    private val EDGE_PUNCTUATION = setOf('.', ',', ';', ':', '!', '?', '\"', '\'', '“', '”', '‘', '’')
    private val WHITESPACE = Regex("\\s+")
    private val MATH_OPERATOR_SPACING = Regex("\\s*([()^+=*/-])\\s*")
    private val COUNTER_EXAMPLE = Regex("\\bcounter[\\s-]+examples?\\b")
}
