package com.revisionapp.domain.course

import com.revisionapp.data.content.CourseCatalogLoader
import com.revisionapp.data.sync.ContentApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CourseCatalogTest {
    private val manifest = """
        {"schemaVersion":2,"courses":[{"id":"sample","name":"Sample Course","file":"courses/sample.json","description":"An example course."}]}
    """.trimIndent()

    private val courseFile = """
        {
          "id":"sample",
          "name":"Sample Course",
          "description":"A tiny remote test course.",
          "sections":[{"id":"topic-a","title":"Topic A","topicLabel":"Topic A","topicIds":["topic-a"],"lessonIds":["first","review"]}],
          "lessons":[
            {"id":"first","title":"First","sectionId":"topic-a","questionIds":["q1"]},
            {"id":"review","title":"Review","sectionId":"topic-a","questionIds":["q1"]}
          ],
          "questions":[{
            "id":"q1","topicId":"topic-a","topicLabel":"Topic A","prompt":"Choose the answer.",
            "answer":{"type":"multiple_choice","options":[{"id":"a","text":"Wrong"},{"id":"b","text":"Right"}],"correctOptionId":"b"},
            "explanation":"The second option is correct."
          }]
        }
    """.trimIndent()

    @Test
    fun storeFetchesOnlyManifestUntilASelectedCourseIsDownloaded() = runTest {
        val requestedPaths = mutableListOf<String>()
        val loader = loader(
            mapOf(
                "manifest.json" to manifest,
                "courses/sample.json" to courseFile,
            ),
            requestedPaths,
        )

        val remoteManifest = loader.loadManifest()
        assertEquals(listOf("manifest.json"), requestedPaths)
        assertEquals("An example course.", remoteManifest.courses.single().description)

        val course = loader.downloadCourse(remoteManifest.courses.single())
        assertEquals(listOf("manifest.json", "courses/sample.json"), requestedPaths)
        assertEquals("q1", course.questionsFor(course.orderedLessons().last()).single().id)
    }

    @Test
    fun manifestRejectsPathsThatCouldEscapeItsCourseDirectory() = runTest {
        val invalidManifest = """
            {"schemaVersion":2,"courses":[{"id":"sample","name":"Sample Course","file":"courses/../outside.json"}]}
        """.trimIndent()

        val result = runCatching { loader(mapOf("manifest.json" to invalidManifest)).loadManifest() }
        assertTrue(result.isFailure)
    }

    @Test
    fun initiallyWrongQuestionsAreQueuedAtTheEndForOneRetryOnly() {
        fun question(id: String) = CourseQuestion(
            id = id,
            topicId = "topic-a",
            topicLabel = "Topic A",
            prompt = "Answer the question.",
            answer = QuestionAnswer.TextInput(listOf("answer")),
            explanation = "Explanation.",
        )
        val first = question("first")
        val second = question("second")
        val queue = CourseQuestionRetryQueue(listOf(first, second))

        assertTrue(queue.scheduleRetry(first, queueIndex = 0))
        assertTrue(queue.scheduleRetry(second, queueIndex = 1))
        assertEquals(listOf("first", "second", "first", "second"), queue.snapshot().map { it.id })
        assertTrue(queue.isRetry(queueIndex = 2))
        assertFalse(queue.scheduleRetry(first, queueIndex = 2))
        assertEquals(listOf("first", "second", "first", "second"), queue.snapshot().map { it.id })
    }

    @Test
    fun questionCheckerSupportsBothV2AnswerTypesAndReasonableTextVariants() {
        val text = QuestionAnswer.TextInput(listOf("x^2 - x - 6", "x squared minus x minus six"))
        val sufficient = QuestionAnswer.TextInput(listOf("sufficient", "sufficient condition"))
        val counterexample = QuestionAnswer.TextInput(
            listOf("one counterexample", "a counterexample", "a counter example", "a counter-example"),
        )
        val choice = QuestionAnswer.MultipleChoice(
            options = listOf(CourseOption("a", "Wrong"), CourseOption("b", "Right")),
            correctOptionId = "b",
        )

        assertTrue(CourseAnswerChecker.check(text, "  X^2   - X - 6 "))
        assertTrue(CourseAnswerChecker.check(sufficient, "Sufficient."))
        assertTrue(CourseAnswerChecker.check(sufficient, "sufficient condition"))
        assertTrue(CourseAnswerChecker.check(counterexample, "A COUNTER EXAMPLE!"))
        assertTrue(CourseAnswerChecker.check(counterexample, "a counter-example."))
        assertTrue(CourseAnswerChecker.check(choice, "b"))
        assertFalse(CourseAnswerChecker.check(choice, "a"))
    }

    private fun loader(files: Map<String, String>, requestedPaths: MutableList<String> = mutableListOf()): CourseCatalogLoader {
        val http = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath.substringAfter("/content-v2/")
                requestedPaths += path
                val body = files[path]
                if (body == null) {
                    respond("not found", HttpStatusCode.NotFound)
                } else {
                    respond(body, HttpStatusCode.OK)
                }
            },
        )
        return CourseCatalogLoader(ContentApiClient(http) { "https://example.test/content-v2" })
    }
}
