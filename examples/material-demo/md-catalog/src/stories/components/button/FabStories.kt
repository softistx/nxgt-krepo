package com.softistx.material.demo.stories.components.button

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.ButtonColor
import com.softistx.material.components.button.Fab
import com.softistx.material.components.button.FabAction
import com.softistx.material.components.button.FabMenu
import com.softistx.material.components.button.SplitButton
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.foundation.icon.StxIcons

/** The Fab stories of the Buttons group. */
internal fun StoryGroupBuilder.fabStories() {
    story("Fab") { knobs ->
        Fab(
            icon = StxIcons.Add,
            description = "New order",
            onClick = {},
            text = knobs.text("Label", "New order").ifBlank { null },
            expanded = knobs.flag("Expanded", true),
            color = knobs.enumChoice("Color", ButtonColor.Primary),
        )
    }

    story("Fab menu") { _ ->
        var open by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.BottomEnd) {
            FabMenu(
                expanded = open,
                onExpandedChange = { open = it },
                actions =
                    listOf(
                        FabAction("New order", StxIcons.Add, onClick = { open = false }),
                        FabAction("Edit", StxIcons.Edit, onClick = { open = false }),
                        FabAction("Delete", StxIcons.Delete, onClick = { open = false }),
                    ),
            )
        }
    }

    story("Split button") { knobs ->
        SplitButton(
            text = knobs.text("Label", "Save"),
            onClick = {},
            overflow =
                listOf(
                    MenuItem("Save as draft", onClick = {}),
                    MenuItem("Save and publish", onClick = {}),
                ),
            enabled = knobs.flag("Enabled", true),
        )
    }
}
