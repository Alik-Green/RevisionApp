package com.revisionapp.di

import app.cash.sqldelight.db.SqlDriver
import com.revisionapp.data.AppJson
import com.revisionapp.data.content.CourseCatalogLoader
import com.revisionapp.data.db.RevisionDatabase
import com.revisionapp.data.repository.SqlLearnedAnswerStore
import com.revisionapp.data.repository.SqlLibraryRepository
import com.revisionapp.data.repository.SqlPackStore
import com.revisionapp.data.repository.SqlProgressRepository
import com.revisionapp.data.repository.SqlSettingsStore
import com.revisionapp.data.sync.ContentApiClient
import com.revisionapp.data.sync.ContentSync
import com.revisionapp.domain.repository.LearnedAnswerStore
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
    const val V2_COURSE_CATALOG_CACHE: String = "content.v2.catalogCache"
    const val DESIRED_RETENTION: String = "srs.desiredRetention"
    const val THEME_MODE: String = "appearance.themeMode"
    const val THEME_STYLE: String = "appearance.themeStyle"
    const val LEARNER_PROGRESS_V2: String = "learner.progress.v2"
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

    private val driver: SqlDriver = platform.createDatabaseDriver()
    private val database: RevisionDatabase = RevisionDatabase(driver)
    private val httpClient = platform.createHttpClient()

    init {
        // Tables added after a user's database already existed. Schema.create only
        // runs against a fresh file, so an existing install would fail with "no such
        // table" the first time an override was learned. Idempotent DDL is used
        // rather than a SQLDelight .sqm migration because versioned migrations need
        // a checked-in schema directory and cannot be verified without a local
        // compiler. See docs/DECISIONS.md D36.
        driver.execute(null, LEARNED_ANSWER_DDL, 0, null)
    }

    val library: LibraryRepository = SqlLibraryRepository(database, json, clock)
    val progress: ProgressRepository = SqlProgressRepository(database)
    val packs: PackStore = SqlPackStore(database, json, clock)
    val settings: SettingsStore = SqlSettingsStore(database)
    val learned: LearnedAnswerStore = SqlLearnedAnswerStore(database, clock)

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

    /** User-editable source for legacy card-pack sync. */
    fun contentBaseUrl(): String =
        settings.read(SettingKeys.CONTENT_BASE_URL)?.takeIf { it.isNotBlank() } ?: ContentSync.DEFAULT_BASE_URL

    /** Fixed repository source for V2 courses; legacy card-pack sync remains separate. */
    fun courseCatalogLoader(): CourseCatalogLoader =
        CourseCatalogLoader(ContentApiClient(httpClient) { CourseCatalogLoader.DEFAULT_BASE_URL }, json)

    fun contentSync(): ContentSync =
        ContentSync(ContentApiClient(httpClient) { contentBaseUrl() }, packs, json)

    private companion object {
        /** Must match LearnedAnswer.sq exactly; it is the same table, created twice. */
        const val LEARNED_ANSWER_DDL: String =
            "CREATE TABLE IF NOT EXISTS learned_answer (" +
                "card_id TEXT NOT NULL PRIMARY KEY, " +
                "answers TEXT NOT NULL, " +
                "updated_at INTEGER NOT NULL)"
    }
}
