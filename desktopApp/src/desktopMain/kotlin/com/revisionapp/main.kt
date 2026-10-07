package com.revisionapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.revisionapp.platform.createPlatformServices
import com.revisionapp.platform.defaultPlatformContext

fun main() {
    val platform = createPlatformServices(defaultPlatformContext())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(),
            title = "Revision",
        ) {
            App(platform)
        }
    }
}
