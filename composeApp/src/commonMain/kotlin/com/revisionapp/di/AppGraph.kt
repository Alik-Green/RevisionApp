package com.revisionapp.di

import com.revisionapp.data.AppJson
import com.revisionapp.data.db.RevisionDatabase
import com.revisionapp.data.repository.SqlLibraryRepository
import com.revisionapp.data.repository.SqlPackStore
import com.revisionapp.data.repository.SqlProgressRepository
import com.revisionapp.data.repository.SqlSettingsStore
import com.revisionapp.data.sync.ContentApiClient
import com.revisionapp.data.sync.ContentSync
import com.revisionapp.domain.repository.LibraryRepository
import com.revisionapp.domain.repository.PackStore
import com.revisionapp.domain.repository.ProgressRepository
import com.revisionapp.domain.repository.SettingsStore
import com.revisionapp.domain.srs.FsrsParameters
import com.revisionapp.domain.srs.FsrsScheduler
import com.revisionapp.domain.usecase.StudyPlanner
import com.revisionapp.platform.PlatformServices
import com.revisionapp.ui.render.RichTextRenderer
import com.revisionapp.ui.render.UnicodeRichTextRenderer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** Setting keys stored in the `setting` table. */
object SettingKeys {
    const val CONTENT_BASE_URL: String = "content.baseUrl"
    const val DESIRED_RETENTION: String = "srs.desiredRetention"
    const val LAST_SYNC_AT: String = "sync.lastAt"
    const val LAST_SYNC_SUMMARY: String = "sync.lastSummary"
}

/**
 * The whole object graph, built once per process from [PlatformServices].
 *
 * Manual constructor injection rather than a DI framework: the graph is small,
 * has no scopes and is created eagerly at startup, so a framework would add a
 * dependency and a DSL without removing any code. See docs/DECISIONS.md D5.
 */
class AppGraph(platform: PlatformServices) {

    val json: Json = AppJson.instance
    val clock: Clock = Clock.System
    val platformName: String = platform.platformName
    val dataDirectory: String = platform.dataDirectory

    private val database: RevisionDatabase = RevisionDatabase(platform.createDatabaseDriver())
    private val httpClient = platform.createHttpClient()

    val library: LibraryRepository = SqlLibraryRepository(database, json, clock)
    val progress: ProgressRepository = SqlProgressRepository(database)
    val packs: PackStore = SqlPackStore(database, json, clock)
    val settings: SettingsStore = SqlSettingsStore(database)

    val planner: StudyPlanner = StudyPlanner(library, progress)
    val renderer: RichTextRenderer = UnicodeRichTextRenderer()

    /** The scheduler honours a user-adjustable desired retention. */
    fun scheduler(): FsrsScheduler {
        val retention = settings.read(SettingKeys.DESIRED_RETENTION)?.toDoubleOrNull()
        val parameters = if (retention == null) {
            FsrsParameters()
        } else {
            runCatching { FsrsParameters(desiredRetention = retention) }.getOrDefault(FsrsParameters())
        }
        return FsrsScheduler(parameters)
    }

    /** The content base URL is user-editable, with a sensible default constant. */
    fun contentBaseUrl(): String =
        settings.read(SettingKeys.CONTENT_BASE_URL)?.takeIf { it.isNotBlank() } ?: ContentSync.DEFAULT_BASE_URL

    fun contentSync(): ContentSync =
        ContentSync(ContentApiClient(httpClient) { contentBaseUrl() }, packs, json)
}
