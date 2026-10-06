package com.revisionapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.revisionapp.di.AppGraph
import com.revisionapp.platform.PlatformServices
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppShell
import com.revisionapp.ui.components.ProvideRichTextRenderer
import com.revisionapp.ui.screens.CardEditorScreen
import com.revisionapp.ui.screens.LibraryScreen
import com.revisionapp.ui.screens.MathGalleryScreen
import com.revisionapp.ui.screens.SettingsScreen
import com.revisionapp.ui.screens.StatsScreen
import com.revisionapp.ui.screens.StudyScreen
import com.revisionapp.ui.screens.TagEditorScreen
import com.revisionapp.ui.screens.TopicEditorScreen
import com.revisionapp.ui.theme.InkPaperTheme

/**
 * The whole UI: one state holder, one nav bar, and an exhaustive `when` over
 * [Route]. Navigation is a sealed class rather than a library so that adding a
 * screen without handling it is a compile error on every target.
 *
 * The maths renderer is provided through composition so every screen reaches it
 * without threading it through parameters, and so the Math Gallery can show a
 * different one beside the app's own.
 */
@Composable
fun App(platform: PlatformServices) {
    val scope = rememberCoroutineScope()
    val graph = remember(platform) { AppGraph(platform) }
    val appState = remember(graph) { AppState(graph, scope) }
    LaunchedEffect(appState) { appState.start() }

    val themeMode = appState.settingsUi.collectAsState().value.themeMode
    InkPaperTheme(themeMode) {
        ProvideRichTextRenderer(appState.renderer) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val route = appState.route.collectAsState().value
                AppShell(appState, route) {
                    when (route) {
                        Route.Library -> LibraryScreen(appState)
                        Route.Study -> StudyScreen(appState)
                        Route.Stats -> StatsScreen(appState)
                        Route.Settings -> SettingsScreen(appState)
                        Route.MathGallery -> MathGalleryScreen(appState)
                        is Route.EditCard -> CardEditorScreen(appState, route.cardId, route.presetTopicId)
                        is Route.EditTopic -> TopicEditorScreen(appState, route.topicId, route.presetParentId)
                        is Route.EditTag -> TagEditorScreen(appState, route.tagId)
                    }
                }
            }
        }
    }
}
