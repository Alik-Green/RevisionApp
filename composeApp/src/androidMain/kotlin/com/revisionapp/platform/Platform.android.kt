package com.revisionapp.platform

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.revisionapp.data.db.RevisionDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal const val DATABASE_FILE_NAME: String = "revisionapp.db"

/** Wraps the Android application context. Never stored in a composable. */
actual class PlatformContext(
    val applicationContext: Context,
)

actual fun createPlatformServices(context: PlatformContext): PlatformServices =
    AndroidPlatformServices(context.applicationContext)

private class AndroidPlatformServices(
    private val applicationContext: Context,
) : PlatformServices {
    override val platformName: String = "Android ${android.os.Build.VERSION.SDK_INT}"

    override val dataDirectory: String
        get() = applicationContext.filesDir.absolutePath

    override fun createDatabaseDriver() = AndroidSqliteDriver(
        schema = RevisionDatabase.Schema,
        context = applicationContext,
        name = DATABASE_FILE_NAME,
    )

    override fun createHttpClient(): HttpClient = HttpClient(OkHttp)
}
