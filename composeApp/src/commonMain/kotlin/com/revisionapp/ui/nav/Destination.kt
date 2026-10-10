package com.revisionapp.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import com.revisionapp.ui.Route

/** The four primary destinations, in bottom-bar order. */
enum class Destination(val route: Route, val label: String, val icon: ImageVector) {
    Study(Route.Study, "Study", Icons.Filled.PlayArrow),
    Store(Route.CourseStore, "Store", Icons.Filled.Storefront),
    Progression(Route.Progression, "Progress", Icons.Filled.EmojiEvents),
    Profile(Route.Profile, "Profile", Icons.Filled.Person),
    ;

    companion object {
        /** The destination a route belongs to, or null for a pushed sub-screen. */
        fun of(route: Route): Destination? = entries.firstOrNull { it.route == route }

        /** Settings, the old library, designers and editors are secondary routes. */
        fun owning(route: Route): Destination = when (route) {
            Route.Study, is Route.Lesson -> Study
            Route.CourseStore -> Store
            Route.Progression, Route.Stats, Route.Achievements -> Progression
            Route.Profile,
            Route.CharacterCustomizer,
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
