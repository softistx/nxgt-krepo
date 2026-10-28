package com.softistx.material.demo.stories.components.button

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.BusyButton
import com.softistx.material.components.button.ConfirmButton
import com.softistx.material.components.button.CopyButton
import com.softistx.material.components.button.IconBadge
import com.softistx.material.components.button.MoreMenu
import com.softistx.material.components.button.OverflowAction
import com.softistx.material.components.button.OverflowBar
import com.softistx.material.components.button.ViewMode
import com.softistx.material.components.button.ViewToggle
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.icon.StxIcons

/** The ButtonUtility stories of the Buttons group. */
internal fun StoryGroupBuilder.buttonUtilityStories() {
    story("Copy button") { knobs ->
        CopyButton(
            text = knobs.text("Value", "ord_9f3a"),
            enabled = knobs.flag("Enabled", true),
        )
    }

    story("Confirm button") { knobs ->
        ConfirmButton(
            text = knobs.text("Label", "Delete"),
            onConfirm = {},
            confirmText = "Confirm delete?",
            enabled = knobs.flag("Enabled", true),
        )
    }

    story("Busy button") { knobs ->
        BusyButton(
            text = knobs.text("Label", "Save changes"),
            onClick = {},
            busy = knobs.flag("Busy", true),
            enabled = knobs.flag("Enabled", true),
        )
    }

    story("More menu") { _ ->
        MoreMenu(
            items =
                listOf(
                    MenuItem("Edit", onClick = {}, leading = StxIcons.Edit),
                    MenuItem("Delete", onClick = {}, leading = StxIcons.Delete),
                ),
        )
    }

    story("Overflow bar") { knobs ->
        val max = knobs.number("Max visible", 2f, 1f..5f, steps = 3).toInt()
        Box(modifier = Modifier.width(280.dp)) {
            OverflowBar(
                actions =
                    listOf(
                        OverflowAction("Edit", StxIcons.Edit, onClick = {}),
                        OverflowAction("Delete", StxIcons.Delete, onClick = {}),
                        OverflowAction("Search", StxIcons.Search, onClick = {}),
                        OverflowAction("Share", StxIcons.Copy, onClick = {}),
                    ),
                maxVisible = max,
            )
        }
    }

    story("Icon badge") { knobs ->
        IconBadge(
            icon = StxIcons.Inbox,
            description = "Inbox",
            onClick = {},
            count = knobs.number("Count", 3f, 0f..120f, steps = 23).toInt(),
        )
    }

    story("View toggle") { _ ->
        var mode by remember { mutableStateOf(ViewMode.List) }
        ViewToggle(value = mode, onChange = { mode = it })
    }
}
