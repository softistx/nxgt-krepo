package com.softistx.material.demo.stories.components.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.surface.ContextMenu
import com.softistx.material.components.surface.HoverCard
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.components.surface.SwipeActions
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/** The Contextual stories of the Surfaces group. */
internal fun StoryGroupBuilder.contextualStories() {
    story("Hover card") { _ ->
        HoverCard(
            title = "Amara Diallo",
            text = "Customer since 2024. Last order this morning.",
            action = "Open",
            onAction = {},
        ) {
            Button(text = "Amara Diallo", onClick = {}, variant = ButtonVariant.Ghost)
        }
    }

    story("Context menu") { _ ->
        ContextMenu(
            items =
                listOf(
                    MenuItem("Edit", onClick = {}, leading = StxIcons.Edit),
                    MenuItem("Delete", onClick = {}, leading = StxIcons.Delete),
                ),
        ) {
            ListTile(title = "Right-click or long-press", supporting = "Opens the menu at the pointer")
        }
    }

    story("Swipe actions") { _ ->
        SwipeActions(
            onDismiss = {},
            background = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(StxTheme.colors.tone(Tone.Error).main)
                        .padding(StxTheme.spacing.md),
                ) {
                    Typography(text = "Delete", color = StxTheme.colors.tone(Tone.Error).onMain)
                }
            },
            customActions = listOf(CustomAccessibilityAction("Delete") { true }),
        ) {
            ListTile(title = "Amara Diallo", supporting = "Swipe to reveal")
        }
    }
}
