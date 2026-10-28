package com.softistx.material.demo.stories.components.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.navigation.FloatingToolbar
import com.softistx.material.components.navigation.NavigationDestination
import com.softistx.material.components.navigation.NavigationSuite
import com.softistx.material.components.navigation.Search
import com.softistx.material.components.navigation.SegmentedControl
import com.softistx.material.components.navigation.Tabs
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/** The NavigationSuite stories of the Navigation group. */
internal fun StoryGroupBuilder.navigationSuiteStories() {
    story("Navigation suite") { knobs ->
        var selected by remember { mutableIntStateOf(0) }
        val destinations =
            listOf(
                NavigationDestination("Home", StxIcons.Home),
                NavigationDestination(
                    label = "Inbox",
                    icon = StxIcons.Inbox,
                    supporting = "Amara, Jonas",
                    badge = "3",
                    badgeTone = Tone.Info,
                ),
                NavigationDestination(
                    label = "Drafts",
                    icon = StxIcons.Edit,
                    unread = true,
                ),
                NavigationDestination(
                    label = "Amara Diallo",
                    icon = StxIcons.Person,
                    avatar = true,
                    supporting = "You",
                ),
                NavigationDestination(
                    label = "Reports",
                    icon = StxIcons.Search,
                    chip = "Beta",
                    shortcut = "⌘R",
                    section = "Workspace",
                ),
                NavigationDestination(
                    label = "Sync",
                    icon = StxIcons.Schedule,
                    busy = true,
                    tone = Tone.Info,
                ),
                NavigationDestination(
                    label = "Settings",
                    icon = StxIcons.Menu,
                    shortcut = "⌘,",
                    section = "Account",
                ),
            )
        NavigationSuite(
            destinations = destinations,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.height(420.dp),
            primaryAction =
                if (knobs.flag("Primary action", true)) {
                    { Button(text = "New", onClick = {}) }
                } else {
                    null
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Typography(
                    text = destinations[selected].label,
                    variant = TypographyVariant.HeadlineSmall,
                )
            }
        }
    }

    story("Tabs") { knobs ->
        var selected by remember { mutableIntStateOf(0) }
        val labels = listOf("Paid", "Pending", "Refunded", "Disputed")
        Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
            Tabs(
                labels = labels,
                selected = selected,
                onSelect = { selected = it },
                scrollable = knobs.flag("Scrollable", false),
                enabled = knobs.flag("Enabled", true),
            )
            Typography(text = "${labels[selected]} orders")
        }
    }

    story("Segmented control") { knobs ->
        var selected by remember { mutableIntStateOf(0) }
        SegmentedControl(
            options = listOf("Day", "Week", "Month"),
            selected = selected,
            onSelect = { selected = it },
            enabled = knobs.flag("Enabled", true),
            icons = if (knobs.flag("Icons", false)) listOf(StxIcons.ViewList, StxIcons.ViewGrid, StxIcons.Calendar) else emptyList(),
        )
    }

    story("Floating toolbar") { knobs ->
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            FloatingToolbar(expanded = knobs.flag("Expanded", true)) {
                IconButton(icon = StxIcons.Edit, description = "Edit", onClick = {}, variant = ButtonVariant.Ghost)
                IconButton(icon = StxIcons.Delete, description = "Delete", onClick = {}, variant = ButtonVariant.Ghost)
                IconButton(icon = StxIcons.Add, description = "Add", onClick = {}, variant = ButtonVariant.Ghost)
            }
        }
    }
}
