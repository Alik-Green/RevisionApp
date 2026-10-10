package com.revisionapp.domain.course

import com.revisionapp.data.content.CourseCatalogLoader
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CourseCatalogTest {
    @OptIn(ExperimentalResourceApi::class)
    @Test
    fun bundledV2CoursesHaveValidOrderedPathsAndReusableQuestions() = runTest {
        val catalog = CourseCatalogLoader.load()

        assertTrue(catalog.validationErrors().isEmpty())
        assertEquals(2, catalog.courses.size)
        val tmua = catalog.course("tmua") ?: error("TMUA course missing")
        assertEquals(
            listOf("tmua-implications", "tmua-proof-moves", "tmua-surds", "tmua-spiral-review"),
            tmua.orderedLessons().map { it.id },
        )
        assertEquals("tmua-contrapositive", tmua.orderedLessons().last().questionIds.first())
        assertEquals("logic", tmua.questionsFor(tmua.orderedLessons().last()).first().topicId)
    }

    @Test
    fun questionCheckerSupportsBothV2AnswerTypes() {
        val text = QuestionAnswer.TextInput(listOf("x^2 - x - 6", "x squared minus x minus six"))
        val choice = QuestionAnswer.MultipleChoice(
            options = listOf(CourseOption("a", "Wrong"), CourseOption("b", "Right")),
            correctOptionId = "b",
        )

        assertTrue(CourseAnswerChecker.check(text, "  X^2   - X - 6 "))
        assertTrue(CourseAnswerChecker.check(choice, "b"))
        assertFalse(CourseAnswerChecker.check(choice, "a"))
    }
}
