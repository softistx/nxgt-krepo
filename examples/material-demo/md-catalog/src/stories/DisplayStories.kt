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
import com.strange.material.display.AnnouncementBar
import com.strange.material.display.Card
import com.strange.material.display.CardVariant
import com.strange.material.display.Chip
import com.strange.material.display.CodeBlock
import com.strange.material.display.Comment
import com.strange.material.display.EmptyState
import com.strange.material.display.ExpandableText
import com.strange.material.display.FileChip
import com.strange.material.display.FilterBar
import com.strange.material.display.Kbd
import com.strange.material.display.LabeledDivider
import com.strange.material.display.LinkPreview
import com.strange.material.display.ListTile
import com.strange.material.display.MentionChip
import com.strange.material.display.MessageBubble
import com.strange.material.display.PinBar
import com.strange.material.display.QuoteBlock
import com.strange.material.display.Rating
import com.strange.material.display.Reaction
import com.strange.material.display.ReactionBar
import com.strange.material.display.ReplyPreview
import com.strange.material.display.SectionHeader
import com.strange.material.display.Skeleton
import com.strange.material.display.Stat
import com.strange.material.display.StatusBadge
import com.strange.material.display.StatusDot
import com.strange.material.display.SuggestionChip
import com.strange.material.display.toggleReaction
import com.strange.material.icon.Icon
import com.strange.material.icon.IconSize
import com.strange.material.icon.StrangeIcons
import com.strange.material.text.Emphasis
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

        story("Suggestion chip") { _ ->
            var query by remember { mutableStateOf("") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                listOf("Amara Diallo", "Jonas Weber", "Priya Raman").forEach { name ->
                    SuggestionChip(text = name, onClick = { query = name })
                }
            }
            if (query.isNotEmpty()) {
                Typography(text = query, emphasis = Emphasis.Medium)
            }
        }

        story("File chip") { _ ->
            var files by remember { mutableStateOf(listOf("invoice.pdf" to "240 KB", "brief.docx" to "1.2 MB")) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                files.forEach { (name, size) ->
                    FileChip(
                        name = name,
                        sizeLabel = size,
                        onRemove = { files = files.filterNot { it.first == name } },
                    )
                }
            }
        }

        story("Section header") { _ ->
            SectionHeader(
                title = "Recent orders",
                supporting = "Last 7 days",
                action = { Button(text = "See all", onClick = {}, variant = ButtonVariant.Link) },
            )
        }

        story("Code block") { _ ->
            CodeBlock(text = "curl https://api.strange.dev/orders/ord_9f3a")
        }

        story("Expandable text") { knobs ->
            ExpandableText(
                text =
                    knobs.text(
                        "Text",
                        "The payout was held because two invoices could not be reconciled. " +
                            "Amara flagged both on Tuesday; the bank has not answered. " +
                            "Until they do, the balance stays in reserve and the dashboard " +
                            "will keep showing this note on every order in the batch.",
                    ),
                collapsedLines = knobs.number("Lines", 3f, 1f..6f, steps = 4).toInt(),
            )
        }

        story("Reaction bar") { _ ->
            var reactions by remember {
                mutableStateOf(
                    listOf(
                        Reaction("👍", 12, selected = true),
                        Reaction("🎉", 4),
                        Reaction("❤️", 2),
                    ),
                )
            }
            ReactionBar(
                reactions =
                    reactions.mapIndexed { index, reaction ->
                        reaction.copy(
                            onClick = {
                                val (count, selected) = toggleReaction(reaction.count, reaction.selected)
                                reactions =
                                    reactions.mapIndexed { i, item ->
                                        if (i == index) item.copy(count = count, selected = selected) else item
                                    }
                            },
                        )
                    },
            )
        }

        story("Announcement bar") { knobs ->
            var visible by remember { mutableStateOf(true) }
            if (visible) {
                AnnouncementBar(
                    text = knobs.text("Text", "The bank is delayed until Tuesday."),
                    tone = knobs.enumChoice("Tone", Tone.Warning),
                    onDismiss = { visible = false },
                    action = { Button(text = "Details", onClick = {}, variant = ButtonVariant.Link) },
                )
            } else {
                Button(text = "Show again", onClick = { visible = true }, variant = ButtonVariant.Ghost)
            }
        }

        story("Quote block") { knobs ->
            QuoteBlock(
                text = knobs.text("Text", "Ship the small thing. The large thing is made of those."),
                attribution = knobs.text("Attribution", "Amara Diallo"),
            )
        }

        story("Link preview") { _ ->
            LinkPreview(
                title = "Orders API",
                url = "https://api.strange.dev/orders",
                description = "Create, list and refund orders.",
                onClick = {},
            )
        }

        story("Message bubble") { knobs ->
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                MessageBubble(
                    text = knobs.text("Incoming", "The payout landed this morning."),
                    name = "Amara Diallo",
                    meta = "14:02",
                )
                MessageBubble(
                    text = knobs.text("Outgoing", "Noted, thanks."),
                    outgoing = true,
                    name = "You",
                    meta = "Read",
                )
            }
        }

        story("Comment") { _ ->
            Comment(
                name = "Jonas Weber",
                text = "Ship the small thing. The large thing is made of those.",
                supporting = "2h ago",
                trailing = {
                    ReactionBar(reactions = listOf(Reaction("👍", 3, selected = true), Reaction("🎉", 1)))
                },
            )
        }

        story("Reply preview") { _ ->
            var visible by remember { mutableStateOf(true) }
            if (visible) {
                ReplyPreview(
                    name = "Amara Diallo",
                    text = "The payout landed this morning.",
                    onDismiss = { visible = false },
                )
            } else {
                Button(text = "Reply again", onClick = { visible = true }, variant = ButtonVariant.Ghost)
            }
        }

        story("Pin bar") { knobs ->
            var visible by remember { mutableStateOf(true) }
            if (visible) {
                PinBar(
                    text = knobs.text("Text", "Office closed on Monday."),
                    onClick = {},
                    onDismiss = { visible = false },
                )
            } else {
                Button(text = "Pin again", onClick = { visible = true }, variant = ButtonVariant.Ghost)
            }
        }

        story("Mention chip") { _ ->
            var people by remember { mutableStateOf(listOf("Amara Diallo", "Jonas Weber")) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                people.forEach { name ->
                    MentionChip(
                        name = name,
                        onClick = {},
                        onRemove = { people = people.filterNot { it == name } },
                    )
                }
            }
        }
    }
