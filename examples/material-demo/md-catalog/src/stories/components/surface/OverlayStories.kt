package com.softistx.material.demo.stories.components.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonColor
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.surface.ConfirmDialog
import com.softistx.material.components.surface.Drawer
import com.softistx.material.components.surface.Sheet
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme

/** The Overlay stories of the Surfaces group. */
internal fun StoryGroupBuilder.overlayStories() {
    story("Confirm dialog") { knobs ->
        var visible by remember { mutableStateOf(false) }
        Button(text = "Delete order", onClick = { visible = true }, color = ButtonColor.Danger)
        ConfirmDialog(
            visible = visible,
            title = "Delete this order?",
            text = "The payout will be reversed. This cannot be undone.",
            onConfirm = { visible = false },
            onDismiss = { visible = false },
            destructive = knobs.flag("Destructive", true),
        )
    }

    story("Sheet") { _ ->
        var visible by remember { mutableStateOf(false) }
        Button(text = "Filters", onClick = { visible = true })
        Sheet(visible = visible, onDismiss = { visible = false }) {
            Column(
                modifier = Modifier.padding(StxTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
            ) {
                Typography(text = "Status")
                Button(text = "Paid", onClick = { visible = false }, variant = ButtonVariant.Ghost)
                Button(text = "Pending", onClick = { visible = false }, variant = ButtonVariant.Ghost)
            }
        }
    }

    story("Drawer") { _ ->
        var open by remember { mutableStateOf(false) }
        Drawer(
            open = open,
            onDismiss = { open = false },
            modifier = Modifier.height(360.dp),
            drawer = {
                Column(Modifier.padding(StxTheme.spacing.md)) {
                    ListTile(title = "Inbox", onClick = { open = false })
                    ListTile(title = "People", onClick = { open = false })
                }
            },
        ) {
            Button(text = "Open drawer", onClick = { open = true })
        }
    }
}
