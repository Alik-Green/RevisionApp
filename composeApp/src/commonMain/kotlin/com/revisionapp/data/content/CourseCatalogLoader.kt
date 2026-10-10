package com.revisionapp.data.content

import com.revisionapp.data.AppJson
import com.revisionapp.data.sync.ContentApiClient
import com.revisionapp.domain.course.CourseCatalog
import com.revisionapp.domain.course.CourseCatalogManifest
import com.revisionapp.domain.course.CourseFileReference
import com.revisionapp.domain.course.LearningCourse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Reads the V2 Store manifest and downloads individual course files from one raw GitHub source. */
class CourseCatalogLoader(
    private val api: ContentApiClient,
    private val json: Json = AppJson.instance,
) {
    suspend fun loadManifest(): CourseCatalogManifest {
        val manifestText = api.fetch(MANIFEST_PATH)
        val manifest = json.decodeFromString<CourseCatalogManifest>(manifestText)
        require(manifest.schemaVersion == CourseCatalog.CURRENT_SCHEMA_VERSION) {
            "Unsupported course manifest version ${manifest.schemaVersion}"
        }
        require(manifest.courses.map { it.id }.distinct().size == manifest.courses.size) {
            "Course ids in the manifest must be unique"
        }
        require(manifest.courses.map { it.file }.distinct().size == manifest.courses.size) {
            "Course files in the manifest must be unique"
        }
        manifest.courses.forEach(::validateReference)
        return manifest
    }

    suspend fun downloadCourse(reference: CourseFileReference): LearningCourse {
        validateReference(reference)
        val courseText = api.fetch(reference.file)
        val course = json.decodeFromString<LearningCourse>(courseText)
        require(course.id == reference.id) {
            "Manifest id '${reference.id}' does not match '${course.id}'"
        }
        require(course.name == reference.name) {
            "Manifest name for '${course.id}' does not match course file"
        }
        val errors = CourseCatalog(CourseCatalog.CURRENT_SCHEMA_VERSION, listOf(course)).validationErrors()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return course
    }

    private fun validateReference(reference: CourseFileReference) {
        require(reference.id.isNotBlank()) { "Course id must not be blank" }
        require(reference.name.isNotBlank()) { "Course name must not be blank" }
        require(isValidCoursePath(reference.file)) {
            "Invalid V2 course path '${reference.file}'"
        }
    }

    private fun isValidCoursePath(path: String): Boolean {
        if (!path.startsWith(COURSE_PATH_PREFIX) || !path.endsWith(JSON_SUFFIX)) return false
        return path.split('/').all { segment ->
            segment.isNotBlank() && segment != "." && segment != ".." &&
                segment.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
        }
    }

    companion object {
        /** Fixed repository source; the app downloads only a manifest until the Store is opened. */
        const val DEFAULT_BASE_URL: String =
            "https://raw.githubusercontent.com/Alik-Green/RevisionApp/arena/299725bb-revisionapp/content-v2"
        const val MANIFEST_PATH: String = "manifest.json"

        private const val COURSE_PATH_PREFIX = "courses/"
        private const val JSON_SUFFIX = ".json"
    }
}
