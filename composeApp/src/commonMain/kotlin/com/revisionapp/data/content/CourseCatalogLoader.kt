package com.revisionapp.data.content

import com.revisionapp.data.AppJson
import com.revisionapp.domain.course.CourseCatalog
import com.revisionapp.domain.course.CourseCatalogManifest
import com.revisionapp.domain.course.LearningCourse
import com.revisionapp.resources.Res
import kotlinx.serialization.decodeFromString
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Reads the bundled V2 course manifest and its separately authored course files. */
object CourseCatalogLoader {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): CourseCatalog {
        val manifestText = Res.readBytes(MANIFEST_PATH).decodeToString()
        val manifest = AppJson.instance.decodeFromString<CourseCatalogManifest>(manifestText)
        require(manifest.schemaVersion == CourseCatalog.CURRENT_SCHEMA_VERSION) {
            "Unsupported course manifest version ${manifest.schemaVersion}"
        }

        val courses = manifest.courses.map { reference ->
            require(reference.file.startsWith("files/content-v2/courses/") && ".." !in reference.file) {
                "Invalid V2 course path '${reference.file}'"
            }
            val courseText = Res.readBytes(reference.file).decodeToString()
            val course = AppJson.instance.decodeFromString<LearningCourse>(courseText)
            require(course.id == reference.id) { "Manifest id '${reference.id}' does not match '${course.id}'" }
            require(course.name == reference.name) { "Manifest name for '${course.id}' does not match course file" }
            course
        }
        val catalog = CourseCatalog(manifest.schemaVersion, courses)
        val errors = catalog.validationErrors()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return catalog
    }

    private const val MANIFEST_PATH = "files/content-v2/manifest.json"
}
