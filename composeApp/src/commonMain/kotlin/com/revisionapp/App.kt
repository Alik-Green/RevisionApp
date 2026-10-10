package com.revisionapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.revisionapp.ui.screens.CourseLessonScreen
import com.revisionapp.ui.screens.CourseStoreScreen
import com.revisionapp.ui.screens.CourseStudyScreen
import com.revisionapp.ui.screens.LibraryScreen
import com.revisionapp.ui.screens.MathGalleryScreen
import com.revisionapp.ui.screens.ProfileScreen
import com.revisionapp.ui.screens.ProgressionScreen
import com.revisionapp.ui.screens.SettingsScreen
import com.revisionapp.ui.screens.StudyScreen
import com.revisionapp.ui.screens.TagEditorScreen
import com.revisionapp.ui.screens.TopicEditorScreen
import com.revisionapp.ui.theme.RevisionAppTheme

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

    val settings = appState.settingsUi.collectAsState().value
    RevisionAppTheme(themeStyle = settings.themeStyle, themeMode = settings.themeMode) {
        ProvideRichTextRenderer(appState.renderer) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val route = appState.route.collectAsState().value
                // Keep everything clear of the status bar, the gesture-navigation area and a
                // display cut-out. The inset is zero on desktop and JVM, so nothing here has
                // to be platform-specific.
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AppShell(appState, route) {
                        when (route) {
                            Route.Study -> CourseStudyScreen(appState)
                            Route.CourseStore -> CourseStoreScreen(appState)
                            Route.Progression, Route.Stats -> ProgressionScreen(appState)
                            Route.Profile -> ProfileScreen(appState)
                            Route.Library -> LibraryScreen(appState)
                            Route.LegacyStudy -> StudyScreen(appState)
                            is Route.Lesson -> CourseLessonScreen(appState, route)
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
}
