package com.revisionapp.domain.course

import com.revisionapp.domain.check.EditDistance
import com.revisionapp.domain.check.NegationGuard
import com.revisionapp.domain.check.TextNormaliser
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

/** Multiple choice stays exact; free text accepts authored variants, typos and close wording. */
object CourseAnswerChecker {
    fun check(answer: QuestionAnswer, input: String): Boolean = when (answer) {
        is QuestionAnswer.MultipleChoice -> input == answer.correctOptionId
        is QuestionAnswer.TextInput -> checkText(answer.acceptedAnswers, input)
    }

    fun acceptedAnswerLabel(answer: QuestionAnswer): String = when (answer) {
        is QuestionAnswer.MultipleChoice -> answer.options.firstOrNull { it.id == answer.correctOptionId }?.text.orEmpty()
        is QuestionAnswer.TextInput -> answer.acceptedAnswers.firstOrNull().orEmpty()
    }

    private fun checkText(acceptedAnswers: List<String>, input: String): Boolean {
        if (input.isBlank()) return false
        val normalizedInput = normalize(input)
        if (acceptedAnswers.any { normalize(it) == normalizedInput }) return true

        val inputLogic = inductionImplication(input)
        if (inputLogic != null && acceptedAnswers.any { inductionImplication(it) == inputLogic }) return true

        // A mathematical statement with different or reversed structure must not
        // fall through to order-insensitive word similarity.
        if (containsInductionFormula(input)) return false

        val inputTokens = TextNormaliser.matchTokens(input)
        return acceptedAnswers.asSequence()
            .filter { it.isNotBlank() }
            .any { expected ->
                if (containsInductionFormula(expected)) {
                    false
                } else {
                    fuzzyPhraseMatch(expected, input, inputTokens)
                }
            }
    }

    private fun fuzzyPhraseMatch(expected: String, input: String, inputTokens: List<String>): Boolean {
        if (hasMathStructure(expected) || hasMathStructure(input)) return false

        val expectedGuardTokens = TextNormaliser.matchTokens(expected).toSet()
        if (NegationGuard.findConflict(expectedGuardTokens, inputTokens.toSet()) != null) return false

        val expectedWords = TextNormaliser.tokens(expected).filterNot { it in FUZZY_FILLER_WORDS }
        val inputWords = TextNormaliser.tokens(input).filterNot { it in FUZZY_FILLER_WORDS }
        if (expectedWords.isEmpty() || inputWords.isEmpty()) return false

        val matched = maximumTokenMatches(expectedWords, inputWords)
        val recall = matched.toDouble() / expectedWords.size
        val precision = matched.toDouble() / inputWords.size
        return recall >= PHRASE_COVERAGE_THRESHOLD && precision >= PHRASE_COVERAGE_THRESHOLD
    }

    /**
     * Recognises the equivalent ways of writing the induction implication used
     * by the course (including "if ... then", "assume ... prove ...", and the
     * reversed surface wording "P(k+1) follows from P(k)").
     */
    private fun inductionImplication(value: String): String? {
        val normalized = normalize(value)
        val base = INDUCTION_BASE.findAll(normalized).toList().singleOrNull() ?: return null
        val next = INDUCTION_NEXT.findAll(normalized).toList().singleOrNull() ?: return null
        if (NegationGuard.isNegated(TextNormaliser.matchTokens(value))) return null

        if (base.range.first < next.range.first) {
            val prefix = normalized.substring(0, base.range.first)
            val between = normalized.substring(base.range.last + 1, next.range.first)
            val directArrow = ARROWS.any(between::contains)
            val directRelation = hasNearWord(
                between,
                "implies",
                "imply",
                "means",
                "then",
                "yields",
                "leads",
                "results",
                "causes",
                "produces",
                "ensures",
                "gives",
                "proves",
                "prove",
                "shows",
                "show",
                "entails",
                "guarantees",
                "therefore",
                "hence",
                "gets",
                "get",
                "sufficient",
                "enough",
            )
            val assumptionAndProof = hasNearWord(prefix, "assume", "assuming", "suppose", "supposing", "given") &&
                hasNearWord(between, "prove", "proves", "show", "shows", "establish", "derive", "deduce")
            val clearConditionalResult = hasNearWord(between, "then") ||
                hasNearWord(normalized.substring(next.range.last + 1), "holds", "true") ||
                hasNearWord(prefix, "whenever")
            val conditional = hasNearWord(prefix, "if", "when", "whenever") &&
                !hasNearWord(between, "only") &&
                clearConditionalResult
            if (directArrow || directRelation || assumptionAndProof || conditional) return INDUCTION_CANONICAL
        } else {
            val between = normalized.substring(next.range.last + 1, base.range.first)
            val outcomeAfterSource = hasNearWord(between, "follows", "follow", "results", "result", "arises", "comes") &&
                !hasNearWord(between, "only", "unless")
            val impliedBy = hasNearWord(between, "implied", "deduced", "derived", "guaranteed", "proved", "established") &&
                hasNearWord(between, "by", "from")
            val consequenceOf = hasNearWord(between, "consequence") && hasNearWord(between, "of")
            val conditionAtEnd = hasNearWord(between, "if", "when", "whenever") &&
                (!hasNearWord(between, "only") || hasNearWord(between, "and"))
            val causeAtEnd = hasNearWord(between, "because", "since")
            if (outcomeAfterSource || impliedBy || consequenceOf || conditionAtEnd || causeAtEnd) return INDUCTION_CANONICAL
        }
        return null
    }

    private fun containsInductionFormula(value: String): Boolean {
        val normalized = normalize(value)
        return INDUCTION_BASE.containsMatchIn(normalized) || INDUCTION_NEXT.containsMatchIn(normalized)
    }

    private fun hasMathStructure(value: String): Boolean = MATH_STRUCTURE.containsMatchIn(normalize(value))

    private fun hasNearWord(text: String, vararg targets: String): Boolean {
        val words = WORD_TOKEN.findAll(text.lowercase()).map { it.value }.toList()
        return words.any { word -> targets.any { target -> wordMatchesWithOneTypo(word, target) } }
    }

    private fun maximumTokenMatches(expected: List<String>, input: List<String>): Int {
        val consumed = BooleanArray(input.size)
        var matched = 0
        val orderedExpected = expected.sortedBy { expectedWord ->
            input.count { inputWord -> fuzzyWordMatch(expectedWord, inputWord) }
        }
        for (expectedWord in orderedExpected) {
            val candidate = input.indices
                .filterNot { consumed[it] }
                .filter { fuzzyWordMatch(expectedWord, input[it]) }
                .minByOrNull { EditDistance.between(expectedWord, input[it]) }
            if (candidate != null) {
                consumed[candidate] = true
                matched++
            }
        }
        return matched
    }

    private fun fuzzyWordMatch(expected: String, input: String): Boolean {
        if (expected == input) return true
        if (!expected.all { it.isLetter() } || !input.all { it.isLetter() }) return false
        return wordMatchesWithOneTypo(expected, input)
    }

    private fun wordMatchesWithOneTypo(first: String, second: String): Boolean {
        if (first == second) return true
        if (first.length < MIN_FUZZY_WORD_LENGTH || second.length < MIN_FUZZY_WORD_LENGTH) return false
        if (EditDistance.between(first, second) == 1) return true
        return isAdjacentTransposition(first, second)
    }

    private fun isAdjacentTransposition(first: String, second: String): Boolean {
        if (first.length != second.length) return false
        val differences = first.indices.filter { first[it] != second[it] }
        if (differences.size != 2) return false
        val left = differences[0]
        val right = differences[1]
        return right == left + 1 && first[left] == second[right] && first[right] == second[left]
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
    private val INDUCTION_BASE = Regex("p\\s*\\(\\s*k\\s*\\)")
    private val INDUCTION_NEXT = Regex("p\\s*\\(\\s*k\\s*\\+\\s*1\\s*\\)")
    private val WORD_TOKEN = Regex("[a-z]+")
    private val MATH_STRUCTURE = Regex("[0-9()^=<>+*/]|=>|->|⇒|→|≤|≥")
    private val ARROWS = listOf("=>", "->", "⇒", "→")
    private val FUZZY_FILLER_WORDS = setOf("a", "an", "the", "where", "it", "is", "that", "one", "single")
    private const val INDUCTION_CANONICAL = "p(k)->p(k+1)"
    private const val MIN_FUZZY_WORD_LENGTH = 5
    private const val PHRASE_COVERAGE_THRESHOLD = 0.9
}
