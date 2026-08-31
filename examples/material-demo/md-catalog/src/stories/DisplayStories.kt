package com.strange.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.button.Button
import com.strange.material.button.ButtonVariant
import com.strange.material.demo.knobs.enumChoice
import com.strange.material.demo.storyGroup
import com.strange.material.display.ActionChip
import com.strange.material.display.Alert
import com.strange.material.display.Card
import com.strange.material.display.CardVariant
import com.strange.material.display.Chip
import com.strange.material.display.EmptyState
import com.strange.material.display.FilterBar
import com.strange.material.display.Kbd
import com.strange.material.display.LabeledDivider
import com.strange.material.display.ListTile
import com.strange.material.display.Rating
import com.strange.material.display.Skeleton
import com.strange.material.display.Stat
import com.strange.material.display.StatusBadge
import com.strange.material.display.StatusDot
import com.strange.material.icon.Icon
import com.strange.material.icon.IconSize
import com.strange.material.icon.StrangeIcons
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

val DisplayStories =
    storyGroup("Display") {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                listOf("Paid", "Pending", "Refunded", "Disputed").forEach { label ->
                    Chip(
                        text = label,
                        selected = label in selected,
                        onClick = {
                            selected = if (label in selected) selected - label else selected + label
                        },
                        leading =
                            if (withIcon) {
                                { Icon(icon = StrangeIcons.Person, description = null) }
                            } else {
                                null
                            },
                    )
                }
            }
        }

        story("Status badge") { _ ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                Tone.entries.forEach { StatusBadge(text = it.name.lowercase(), tone = it) }
            }
        }

        story("List tile") { knobs ->
            val supporting = knobs.flag("Supporting text", true)
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs)) {
                listOf("Amara Diallo", "Jonas Weber", "Priya Raman").forEach { name ->
                    ListTile(
                        title = name,
                        supporting = if (supporting) "Last seen this morning" else null,
                        onClick = {},
                        leading = { Icon(icon = StrangeIcons.Person, description = null) },
                        trailing = { StatusBadge(text = "active", tone = Tone.Success) },
                    )
                }
            }
        }

        story("Alert") { knobs ->
            Alert(
                text = knobs.text("Text", "Two invoices could not be reconciled."),
                tone = knobs.enumChoice("Tone", Tone.Warning),
                title = if (knobs.flag("Title", true)) "Reconciliation paused" else null,
                visible = knobs.flag("Visible", true),
                action = { Button(text = "Review", onClick = {}, variant = ButtonVariant.Link) },
            )
        }

        story("Empty state") { knobs ->
            EmptyState(
                title = knobs.text("Title", "No invoices yet"),
                description = "Invoices appear here as soon as an order is settled.",
                illustration = { Icon(icon = StrangeIcons.Inbox, description = null, size = IconSize.XLarge) },
                action = if (knobs.flag("Action", true)) ({ Button("New invoice", {}) }) else null,
            )
        }

        story("Skeleton") { knobs ->
            val rows = knobs.number("Rows", 3f, 1f..6f, steps = 4).toInt()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
            ) {
                Skeleton(height = 24.dp, modifier = Modifier.fillMaxWidth(0.4f))
                repeat(rows) { Skeleton() }
            }
        }

        story("Stat") { _ ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md)) {
                Stat(value = "128", label = "Orders", delta = "+12%", tone = Tone.Success)
                Stat(value = "€4.2k", label = "Revenue", delta = "−3%", tone = Tone.Error)
                Stat(value = "96%", label = "Fulfilled")
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
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md)) {
                Button(text = "Continue with email", onClick = {})
                LabeledDivider(label = knobs.text("Label", "or"))
                Button(text = "Continue as guest", onClick = {}, variant = ButtonVariant.Ghost)
            }
        }

        story("Action chip") { _ ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                ActionChip(
                    text = "Call",
                    onClick = {},
                    leading = { Icon(icon = StrangeIcons.Person, description = null) },
                )
                ActionChip(text = "Add to calendar", onClick = {})
                ActionChip(text = "Open in maps", onClick = {})
            }
        }

        story("Status dot") { _ ->
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                Tone.entries.forEach { tone ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(tone = tone)
                        Typography(text = tone.name.lowercase())
                    }
                }
            }
        }

        story("Keyboard shortcut") { _ ->
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                Kbd(keys = listOf("Ctrl", "K"))
                Kbd(keys = listOf("⌘", "⇧", "P"))
            }
        }
    }
