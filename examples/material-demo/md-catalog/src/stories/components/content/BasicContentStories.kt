package com.softistx.material.demo.stories.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.CardVariant
import com.softistx.material.components.content.Chip
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.EmptyStateDefaults
import com.softistx.material.components.content.FilterBar
import com.softistx.material.components.content.LabeledDivider
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.content.ListTileDefaults
import com.softistx.material.components.content.ListTileGroup
import com.softistx.material.components.content.Rating
import com.softistx.material.components.content.Stat
import com.softistx.material.components.feedback.StatusBadge
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/** The BasicContent stories of the Content group. */
internal fun StoryGroupBuilder.basicContentStories() {
    story("Card") { knobs ->
        Card(
            variant = knobs.enumChoice("Variant", CardVariant.Filled),
            onClick = if (knobs.flag("Clickable", true)) ({ }) else null,
            enabled = knobs.flag("Enabled", true),
        ) {
            Typography(text = "Quarterly report", variant = TypographyVariant.TitleMedium)
            Typography(text = "Updated eight minutes ago by Amara.")
        }
    }

    story("Chip") { knobs ->
        var selected by remember { mutableStateOf(setOf("Paid")) }
        val withIcon = knobs.flag("Leading icon", false)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
            listOf("Paid", "Pending", "Refunded", "Disputed").forEach { label ->
                Chip(
                    text = label,
                    selected = label in selected,
                    onClick = {
                        selected = if (label in selected) selected - label else selected + label
                    },
                    leading =
                        if (withIcon) {
                            { Icon(icon = StxIcons.Person, description = null) }
                        } else {
                            null
                        },
                )
            }
        }
    }

    story("List tile") { knobs ->
        val supporting = knobs.flag("Supporting text", true)
        val grouped = knobs.flag("Segmented group", true)
        val choosable = knobs.flag("Single choice", true)
        var chosen by remember { mutableStateOf(1) }
        val names = listOf("Amara Diallo", "Jonas Weber", "Priya Raman")
        Column(
            verticalArrangement = Arrangement.spacedBy(if (grouped) ListTileDefaults.GroupGap else StxTheme.spacing.xxs),
        ) {
            names.forEachIndexed { index, name ->
                ListTile(
                    title = name,
                    supporting = if (supporting) "Last seen this morning" else null,
                    onClick = { chosen = index },
                    selected = if (choosable) chosen == index else null,
                    group = if (grouped) ListTileGroup(index, names.size) else null,
                    leading = { Icon(icon = StxIcons.Person, description = null) },
                    trailing = { StatusBadge(text = "active", tone = Tone.Success) },
                )
            }
            ListTile(title = "Archived (disabled)", onClick = {}, enabled = false, group = if (grouped) ListTileGroup(0, 1) else null)
        }
    }

    story("Empty state") { knobs ->
        EmptyState(
            title = knobs.text("Title", "No invoices yet"),
            description = "Invoices appear here as soon as an order is settled.",
            illustration = if (knobs.flag("Icon plate", true)) ({ EmptyStateDefaults.IconPlate(StxIcons.Inbox) }) else null,
            action = if (knobs.flag("Action", true)) ({ Button("New invoice", {}) }) else null,
        )
    }

    story("Stat") { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
                Stat(value = "128", label = "Orders", delta = "+12%", tone = Tone.Success)
                Stat(value = "€4.2k", label = "Revenue", delta = "−3%", tone = Tone.Error)
                Stat(value = "96%", label = "Fulfilled")
            }
            // These two share one height, delta or not: the height of a delta is reserved.
            Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
                Stat(value = "12", label = "Students", modifier = Modifier.weight(1f), delta = "+2 this term")
                Stat(value = "3", label = "Absences", modifier = Modifier.weight(1f))
            }
        }
    }

    story("Filter bar") { _ ->
        var selected by remember { mutableStateOf(setOf("Paid")) }
        FilterBar(
            options = listOf("Paid", "Pending", "Refunded", "Disputed"),
            selected = selected,
            onChange = { selected = it },
        )
    }

    story("Rating") { knobs ->
        var stars by remember { mutableStateOf(4) }
        Rating(
            value = stars,
            onChange = { stars = it },
            enabled = knobs.flag("Enabled", true),
        )
    }

    story("Labeled divider") { knobs ->
        Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
            Button(text = "Continue with email", onClick = {})
            LabeledDivider(label = knobs.text("Label", "or"))
            Button(text = "Continue as guest", onClick = {}, variant = ButtonVariant.Ghost)
        }
    }
}
