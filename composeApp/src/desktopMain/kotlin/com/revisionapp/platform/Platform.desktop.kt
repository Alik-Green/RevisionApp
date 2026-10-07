package com.revisionapp.platform

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.revisionapp.data.db.RevisionDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File

internal const val DATABASE_FILE_NAME: String = "revisionapp.db"

/** Wraps the directory that holds the desktop database file. */
actual class PlatformContext(
    val directory: File,
)

/**
 * Default desktop location: `~/.revisionapp`. Overridable with the
 * `revisionapp.dataDir` system property, which the tests and CI use to keep
 * runs isolated from the developer's real data.
 */
fun defaultPlatformContext(): PlatformContext {
    val override = System.getProperty("revisionapp.dataDir")
    val directory = if (override.isNullOrBlank()) {
        File(System.getProperty("user.home"), ".revisionapp")
    } else {
        File(override)
    }
    return PlatformContext(directory)
}

actual fun createPlatformServices(context: PlatformContext): PlatformServices =
    DesktopPlatformServices(context.directory)

private class DesktopPlatformServices(
    private val directory: File,
) : PlatformServices {
    override val platformName: String = "Desktop (JVM)"

    override val dataDirectory: String
        get() = directory.absolutePath

    override fun createDatabaseDriver(): JdbcSqliteDriver {
        directory.mkdirs()
        val databaseFile = File(directory, DATABASE_FILE_NAME)
        val needsSchema = !databaseFile.exists() || databaseFile.length() == 0L
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
        if (needsSchema) {
            RevisionDatabase.Schema.create(driver)
        }
        return driver
    }

    override fun createHttpClient(): HttpClient = HttpClient(OkHttp)
}
