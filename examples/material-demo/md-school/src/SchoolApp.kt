package com.softistx.material.school

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import com.softistx.material.components.button.Fab
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.feedback.Toaster
import com.softistx.material.components.navigation.NavigationDestination
import com.softistx.material.components.navigation.NavigationSuite
import com.softistx.material.components.navigation.NavigationSuiteDefaults
import com.softistx.material.components.surface.Sheet
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.school.common.SchoolCommands
import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolSession
import com.softistx.material.school.session.SchoolStart
import com.softistx.material.school.session.rememberSchoolSession

/**
 * The whole School app. Both launchers call this and nothing else; a headless render calls it with
 * the [initialScreen] and [start] it wants to look at.
 */
@Composable
fun SchoolApp(
    initialScreen: SchoolScreen = SchoolScreen.Dashboard,
    start: SchoolStart = SchoolStart(),
) {
    val session = rememberSchoolSession(initialScreen, start)
    SchoolTheme(session.preferences) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Toaster(state = session.toaster, modifier = Modifier.fillMaxSize()) {
                SchoolNavigation(session)
                SchoolCommands(session)
            }
        }
    }
}

/**
 * Bar on a phone, rail on a tablet and a desktop window: [NavigationSuite] picks. M3's compact bar
 * holds three to five destinations, so on a phone the last two screens sit behind a "More" entry.
 */
@Composable
private fun SchoolNavigation(session: SchoolSession) {
    val compact = LocalWindowInfo.current.containerDpSize.width < NavigationSuiteDefaults.CompactBelow
    val onBar = if (compact) SchoolScreen.entries - MORE_SCREENS else SchoolScreen.entries
    var moreOpen by remember { mutableStateOf(false) }
    val destinations = onBar.map { it.destination(session.unread) }
    NavigationSuite(
        destinations = if (compact) destinations + NavigationDestination("More", StxIcons.MoreHoriz) else destinations,
        selected = onBar.indexOf(session.screen).takeIf { it >= 0 } ?: onBar.size,
        onSelect = { if (it < onBar.size) session.open(onBar[it]) else moreOpen = true },
        primaryAction = {
            Fab(
                icon = StxIcons.Add,
                description = "Enter a grade",
                onClick = { session.open(SchoolScreen.Grades) },
            )
        },
    ) {
        session.screen.content(session)
    }
    Sheet(visible = moreOpen, onDismiss = { moreOpen = false }) {
        MORE_SCREENS.forEach { screen ->
            ListTile(
                title = screen.label,
                leading = { Icon(icon = screen.icon, description = null) },
                onClick = {
                    moreOpen = false
                    session.open(screen)
                },
            )
        }
    }
}

private fun SchoolScreen.destination(unread: Int) =
    NavigationDestination(
        label = label,
        icon = icon,
        badge = if (this == SchoolScreen.Messages && unread > 0) unread.toString() else null,
    )

private val MORE_SCREENS = listOf(SchoolScreen.Timetable, SchoolScreen.Settings)
