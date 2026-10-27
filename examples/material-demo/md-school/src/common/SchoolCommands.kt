package com.softistx.material.school.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.data.CommandItem
import com.softistx.material.components.data.CommandPalette
import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolSession

/** The command palette: jump to a screen, or to the student you were last looking at. */
@Composable
fun SchoolCommands(session: SchoolSession) {
    var query by remember { mutableStateOf("") }
    val close = {
        session.paletteOpen = false
        query = ""
    }
    val items =
        SchoolScreen.entries.mapIndexed { index, screen ->
            CommandItem(
                label = "Go to ${screen.label}",
                onRun = {
                    session.open(screen)
                    close()
                },
                group = "Navigate",
                shortcut = "G ${index + 1}",
            )
        }
    CommandPalette(
        visible = session.paletteOpen,
        query = query,
        onQueryChange = { query = it },
        items = items,
        onDismiss = close,
    )
}
