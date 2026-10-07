package com.revisionapp.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.revisionapp.ui.Route

/**
 * Every top-level destination, in the order they appear.
 *
 * One list, so adding a destination is a one-line change here plus a branch in
 * `App`'s `when` — which the compiler then forces you to write. The navigation bar
 * and the rail are both driven by this, so the two can never disagree about what
 * exists or what order it is in.
 */
enum class Destination(val route: Route, val label: String, val icon: ImageVector) {
    Library(Route.Library, "Library", Icons.Filled.MenuBook),
    Study(Route.Study, "Study", Icons.Filled.PlayArrow),
    Stats(Route.Stats, "Stats", Icons.Filled.BarChart),
    Settings(Route.Settings, "Settings", Icons.Filled.Settings),
    ;

    companion object {
        /** The destination a route belongs to, or null for a pushed sub-screen. */
        fun of(route: Route): Destination? = entries.firstOrNull { it.route == route }

        /**
         * The destination to highlight while a sub-screen is open, so the shell
         * still shows where you are. Editors and the gallery hang off Library and
         * Settings respectively.
         */
        fun owning(route: Route): Destination = when (route) {
            is Route.EditCard, is Route.EditTopic, is Route.EditTag -> Library
            Route.MathGallery -> Settings
            else -> of(route) ?: Library
        }
    }
}

/**
 * Material 3 window-size classes, from the adaptive layout guidance: compact is a
 * phone in portrait, medium a small tablet or a narrow desktop window, expanded
 * anything wider.
 */
enum class WindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    /** Below 600dp the bar fits and the rail would eat width a phone does not have. */
    val usesBottomBar: Boolean get() = this == COMPACT

    companion object {
        fun fromWidth(widthDp: Float): WindowSizeClass = when {
            widthDp < 600f -> COMPACT
            widthDp < 840f -> MEDIUM
            else -> EXPANDED
        }
    }
}
