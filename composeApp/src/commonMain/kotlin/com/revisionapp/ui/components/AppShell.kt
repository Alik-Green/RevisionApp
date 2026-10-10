package com.revisionapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (WindowSizeClass.fromWidth(maxWidth.value).usesBottomBar) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) { screen() }
                NavigationBar {
                    for (destination in Destination.entries) {
                        NavigationBarItem(
                            selected = destination == selected,
                            onClick = { state.switchTab(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    for (destination in Destination.entries) {
                        NavigationRailItem(
                            selected = destination == selected,
                            onClick = { state.switchTab(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) { screen() }
            }
        }
    }
}
