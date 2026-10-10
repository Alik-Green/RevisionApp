package com.revisionapp.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import com.revisionapp.ui.Route

/** The three primary mobile destinations, in bottom-bar order. */
enum class Destination(val route: Route, val label: String, val icon: ImageVector) {
    Study(Route.Study, "Study", Icons.Filled.PlayArrow),
    Progression(Route.Progression, "Progression", Icons.Filled.EmojiEvents),
    Profile(Route.Profile, "Profile", Icons.Filled.Person),
    ;

    companion object {
        /** The destination a route belongs to, or null for a pushed sub-screen. */
        fun of(route: Route): Destination? = entries.firstOrNull { it.route == route }

        /** Settings, the old library and editors are secondary Profile routes. */
        fun owning(route: Route): Destination = when (route) {
            Route.Study, is Route.Lesson -> Study
            Route.Progression, Route.Stats -> Progression
            Route.Profile,
            Route.Settings,
            Route.Library,
            Route.LegacyStudy,
            Route.MathGallery,
            is Route.EditCard,
            is Route.EditTopic,
            is Route.EditTag -> Profile
        }
    }
}

/** Material 3 window-size classes, from compact phones to wider layouts. */
enum class WindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    val usesBottomBar: Boolean get() = this == COMPACT

    companion object {
        fun fromWidth(widthDp: Float): WindowSizeClass = when {
            widthDp < 600f -> COMPACT
            widthDp < 840f -> MEDIUM
            else -> EXPANDED
        }
    }
}
