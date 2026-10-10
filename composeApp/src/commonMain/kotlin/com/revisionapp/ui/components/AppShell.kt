package com.revisionapp.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.nav.Destination
import com.revisionapp.ui.nav.WindowSizeClass
import com.revisionapp.ui.session.SessionState

/** Four primary destinations; a lesson or live legacy-card session is immersive. */
@Composable
fun AppShell(state: AppState, current: Route, screen: @Composable () -> Unit) {
    val session = state.sessionState.collectAsState().value
    val immersive = current is Route.Lesson ||
        (current == Route.LegacyStudy && session !is SessionState.Empty && session !is SessionState.Finished)
    if (immersive) {
        screen()
        return
    }

    val selected = Destination.owning(current)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (WindowSizeClass.fromWidth(maxWidth.value).usesBottomBar) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) { screen() }
                if (isDark) {
                    NavigationBar(
                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        BottomNavigationItems(state, selected, isDark = true)
                    }
                } else {
                    NavigationBar { BottomNavigationItems(state, selected, isDark = false) }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                if (isDark) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        RailNavigationItems(state, selected, isDark = true)
                    }
                } else {
                    NavigationRail { RailNavigationItems(state, selected, isDark = false) }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) { screen() }
            }
        }
    }
}

@Composable
private fun BottomNavigationItems(state: AppState, selected: Destination, isDark: Boolean) {
    for (destination in Destination.entries) {
        NavigationBarItem(
            selected = destination == selected,
            onClick = { state.switchTab(destination.route) },
            icon = { Icon(destination.icon, contentDescription = destination.label) },
            label = { Text(destination.label) },
            colors = if (isDark) {
                NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                NavigationBarItemDefaults.colors()
            },
        )
    }
}

@Composable
private fun RailNavigationItems(state: AppState, selected: Destination, isDark: Boolean) {
    for (destination in Destination.entries) {
        NavigationRailItem(
            selected = destination == selected,
            onClick = { state.switchTab(destination.route) },
            icon = { Icon(destination.icon, contentDescription = destination.label) },
            label = { Text(destination.label) },
            colors = if (isDark) {
                NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                NavigationRailItemDefaults.colors()
            },
        )
    }
}
