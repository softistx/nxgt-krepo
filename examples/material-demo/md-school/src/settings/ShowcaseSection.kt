package com.softistx.material.school.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.BusyButton
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.feedback.Progress
import com.softistx.material.components.navigation.SegmentedControl
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.common.say
import com.softistx.material.school.session.DataState
import com.softistx.material.school.session.SchoolSession
import kotlinx.coroutines.delay

/** Showcase only: put every screen on its loading, empty or error face, and fire the toasts. */
@Composable
fun ShowcaseSection(session: SchoolSession) {
    Card {
        SectionHeader(title = "Showcase", supporting = "What every screen looks like in each data state")
        SegmentedControl(
            options = DataState.entries.map { it.label },
            selected = session.data.ordinal,
            onSelect = { session.data = DataState.entries[it] },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Button("Success toast", { session.toaster.say("Saved") }, variant = ButtonVariant.Tonal)
            Button("Error toast", { session.toaster.say("Could not save", Tone.Error) }, variant = ButtonVariant.Outlined)
        }
        var syncing by remember { mutableStateOf(false) }
        LaunchedEffect(syncing) {
            if (syncing) {
                delay(1200)
                syncing = false
                session.finishSync()
            }
        }
        BusyButton(text = "Sync now", onClick = { syncing = true }, busy = syncing, variant = ButtonVariant.Outlined)
        Progress(progress = 0.64f, modifier = Modifier)
    }
}
