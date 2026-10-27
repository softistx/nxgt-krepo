package com.softistx.material.school

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Fab
import com.softistx.material.components.feedback.Toaster
import com.softistx.material.components.navigation.NavigationDestination
import com.softistx.material.components.navigation.NavigationSuite
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.school.common.SchoolCommands
import com.softistx.material.school.data.Conversations
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

/** Bar on a phone, rail on a tablet and a desktop window: [NavigationSuite] picks. */
@Composable
private fun SchoolNavigation(session: SchoolSession) {
    val unread = Conversations.all.sumOf { it.unread }
    NavigationSuite(
        destinations =
            SchoolScreen.entries.map { screen ->
                NavigationDestination(
                    label = screen.label,
                    icon = screen.icon,
                    badge = if (screen == SchoolScreen.Messages && unread > 0) unread.toString() else null,
                )
            },
        selected = session.screen.ordinal,
        onSelect = { session.open(SchoolScreen.entries[it]) },
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
}
