package com.revisionapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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

/**
 * The adaptive navigation shell: a bottom bar on compact widths, a rail from
 * medium upwards, and nothing at all during a live session, which gets the whole
 * window.
 *
 * Both are driven by [Destination.entries], so the bar and the rail can never
 * disagree about what exists or in what order. The due count lives on the Study
 * destination as a badge — it used to float at the top right of every screen,
 * outside any tab, which read as a global notification rather than as a property
 * of studying.
 */
@Composable
fun AppShell(state: AppState, current: Route, screen: @Composable () -> Unit) {
    val session = state.sessionState.collectAsState().value
    val immersive = current == Route.Study &&
        session !is SessionState.Empty &&
        session !is SessionState.Finished
    if (immersive) {
        screen()
        return
    }

    val snapshot = state.snapshot.collectAsState().value
    val filter = state.filter.collectAsState().value
    val due = snapshot.dueCount(filter)
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
                            icon = { DestinationIcon(destination, due) },
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
                            icon = { DestinationIcon(destination, due) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) { screen() }
            }
        }
    }
}

/**
 * The destination icon, with the due count badged onto Study.
 *
 * `NavigationBarItem` and `NavigationRailItem` have no `badge` parameter in this
 * Material 3 version, so the badge is composed into the icon slot with `BadgedBox`,
 * which is what the parameter did internally anyway.
 */
@Composable
private fun DestinationIcon(destination: Destination, due: Int) {
    if (destination == Destination.Study && due > 0) {
        BadgedBox(badge = { Badge { Text(dueLabel(due)) } }) {
            Icon(destination.icon, contentDescription = destination.label)
        }
    } else {
        Icon(destination.icon, contentDescription = destination.label)
    }
}

/** A three-digit due count would not fit a badge, and precision stops mattering. */
private fun dueLabel(due: Int): String = if (due > 99) "99+" else due.toString()
