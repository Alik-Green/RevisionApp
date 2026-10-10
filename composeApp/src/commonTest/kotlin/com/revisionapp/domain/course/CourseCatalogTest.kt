package com.revisionapp.domain.course

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
        {"schemaVersion":2,"courses":[{"id":"sample","name":"Sample Course","file":"courses/sample.json"}]}
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
    fun loaderFetchesBranchManifestAndReusableCourseFiles() = runTest {
        val catalog = loader(
            mapOf(
                "manifest.json" to manifest,
                "courses/sample.json" to courseFile,
            ),
        ).load()

        assertTrue(catalog.validationErrors().isEmpty())
        val course = catalog.course("sample") ?: error("Sample course missing")
        assertEquals(listOf("first", "review"), course.orderedLessons().map { it.id })
        assertEquals("q1", course.questionsFor(course.orderedLessons().last()).single().id)
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

    private fun loader(files: Map<String, String>): CourseCatalogLoader {
        val http = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath.substringAfter("/content-v2/")
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
