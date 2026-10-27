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
import com.softistx.material.components.content.ActionChip
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.CardVariant
import com.softistx.material.components.content.Chip
import com.softistx.material.components.content.CodeBlock
import com.softistx.material.components.content.Comment
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.ExpandableText
import com.softistx.material.components.content.FeatureItem
import com.softistx.material.components.content.FeatureList
import com.softistx.material.components.content.FileChip
import com.softistx.material.components.content.FilterBar
import com.softistx.material.components.content.Kbd
import com.softistx.material.components.content.LabeledDivider
import com.softistx.material.components.content.LinkPreview
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.content.ListTileDefaults
import com.softistx.material.components.content.ListTileGroup
import com.softistx.material.components.content.MentionChip
import com.softistx.material.components.content.MessageBubble
import com.softistx.material.components.content.PinBar
import com.softistx.material.components.content.Price
import com.softistx.material.components.content.PricingCard
import com.softistx.material.components.content.QuoteBlock
import com.softistx.material.components.content.Rating
import com.softistx.material.components.content.Reaction
import com.softistx.material.components.content.ReactionBar
import com.softistx.material.components.content.ReplyPreview
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.content.Stat
import com.softistx.material.components.content.SuggestionChip
import com.softistx.material.components.content.toggleReaction
import com.softistx.material.components.feedback.StatusBadge
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.demo.storyGroup
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

val ContentStories =
    storyGroup("Content") {
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
                illustration = { Icon(icon = StxIcons.Inbox, description = null, size = IconSize.XLarge) },
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

        story("Action chip") { _ ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                ActionChip(
                    text = "Call",
                    onClick = {},
                    leading = { Icon(icon = StxIcons.Person, description = null) },
                )
                ActionChip(text = "Add to calendar", onClick = {})
                ActionChip(text = "Open in maps", onClick = {})
            }
        }

        story("Keyboard shortcut") { _ ->
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                Kbd(keys = listOf("Ctrl", "K"))
                Kbd(keys = listOf("⌘", "⇧", "P"))
            }
        }

        story("Suggestion chip") { _ ->
            var query by remember { mutableStateOf("") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
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
            CodeBlock(text = "curl https://api.softistx.dev/orders/ord_9f3a")
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

        story("Quote block") { knobs ->
            QuoteBlock(
                text = knobs.text("Text", "Ship the small thing. The large thing is made of those."),
                attribution = knobs.text("Attribution", "Amara Diallo"),
            )
        }

        story("Link preview") { _ ->
            LinkPreview(
                title = "Orders API",
                url = "https://api.softistx.dev/orders",
                description = "Create, list and refund orders.",
                onClick = {},
            )
        }

        story("Message bubble") { knobs ->
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                people.forEach { name ->
                    MentionChip(
                        name = name,
                        onClick = {},
                        onRemove = { people = people.filterNot { it == name } },
                    )
                }
            }
        }

        story("Price") { knobs ->
            Price(
                amount = knobs.text("Amount", "€12"),
                compareAt = knobs.text("Compare at", "€18").ifBlank { null },
                period = knobs.text("Period", "/mo").ifBlank { null },
                tone = if (knobs.flag("Discount tone", true)) Tone.Success else null,
            )
        }

        story("Feature list") { _ ->
            FeatureList(
                items =
                    listOf(
                        FeatureItem("Unlimited orders"),
                        FeatureItem("Priority payouts"),
                        FeatureItem("Audit trail"),
                        FeatureItem("Dedicated account manager", included = false),
                    ),
            )
        }

        story("Pricing card") { knobs ->
            PricingCard(
                name = knobs.text("Name", "Studio"),
                amount = "€29",
                compareAt = "€39",
                period = "/mo",
                description = "For a team that ships every week.",
                features =
                    listOf(
                        FeatureItem("Unlimited orders"),
                        FeatureItem("Priority payouts"),
                        FeatureItem("Dedicated account manager", included = false),
                    ),
                highlighted = knobs.flag("Highlighted", true),
                badge = "Popular".takeIf { knobs.flag("Badge", true) },
                action = { Button(text = "Start", onClick = {}) },
            )
        }
    }
