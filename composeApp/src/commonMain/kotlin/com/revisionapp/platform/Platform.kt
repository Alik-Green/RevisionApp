package com.revisionapp.platform

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient

/**
 * Opaque, platform-provided handle to whatever a platform needs in order to
 * open local storage. Common code never inspects it, it only passes it back to
 * [createPlatformServices], so no platform type ever leaks into `commonMain`.
 */
expect class PlatformContext

/**
 * Everything the app needs from the host platform. Constructed once at the
 * entry point of each target and injected manually into the object graph.
 */
interface PlatformServices {
    /** Name shown on the settings screen, e.g. `Desktop (JVM)`. */
    val platformName: String

    /** Absolute path of the directory that holds the local database. */
    val dataDirectory: String

    /** Opens (and on first use creates) the SQLite database. */
    fun createDatabaseDriver(): SqlDriver

    /** Builds an HTTP client backed by the platform's native engine. */
    fun createHttpClient(): HttpClient
}

expect fun createPlatformServices(context: PlatformContext): PlatformServices
