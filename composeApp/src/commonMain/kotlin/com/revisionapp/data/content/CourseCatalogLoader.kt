package com.revisionapp.data.content

import com.revisionapp.data.AppJson
import com.revisionapp.data.sync.ContentApiClient
import com.revisionapp.domain.course.CourseCatalog
import com.revisionapp.domain.course.CourseCatalogManifest
import com.revisionapp.domain.course.LearningCourse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Loads the V2 catalog and its course files from a raw GitHub branch. */
class CourseCatalogLoader(
    private val api: ContentApiClient,
    private val json: Json = AppJson.instance,
) {
    suspend fun load(): CourseCatalog {
        val manifestText = api.fetch(MANIFEST_PATH)
        val manifest = json.decodeFromString<CourseCatalogManifest>(manifestText)
        require(manifest.schemaVersion == CourseCatalog.CURRENT_SCHEMA_VERSION) {
            "Unsupported course manifest version ${manifest.schemaVersion}"
        }

        val courses = manifest.courses.map { reference ->
            require(isValidCoursePath(reference.file)) {
                "Invalid V2 course path '${reference.file}'"
            }
            val courseText = api.fetch(reference.file)
            val course = json.decodeFromString<LearningCourse>(courseText)
            require(course.id == reference.id) { "Manifest id '${reference.id}' does not match '${course.id}'" }
            require(course.name == reference.name) { "Manifest name for '${course.id}' does not match course file" }
            course
        }
        val catalog = CourseCatalog(manifest.schemaVersion, courses)
        val errors = catalog.validationErrors()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return catalog
    }

    private fun isValidCoursePath(path: String): Boolean {
        if (!path.startsWith(COURSE_PATH_PREFIX) || !path.endsWith(JSON_SUFFIX)) return false
        return path.split('/').all { it.isNotBlank() && it != "." && it != ".." }
    }

    companion object {
        /** Default remote source, kept on the Arena-pinned branch for this release. */
        const val DEFAULT_BASE_URL: String =
            "https://raw.githubusercontent.com/Alik-Green/RevisionApp/arena/299725bb-revisionapp/content-v2"
        const val MANIFEST_PATH: String = "manifest.json"

        private const val COURSE_PATH_PREFIX = "courses/"
        private const val JSON_SUFFIX = ".json"
    }
}
