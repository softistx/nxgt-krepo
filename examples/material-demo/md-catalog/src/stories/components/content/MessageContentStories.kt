package com.softistx.material.demo.stories.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.content.Comment
import com.softistx.material.components.content.LinkPreview
import com.softistx.material.components.content.MentionChip
import com.softistx.material.components.content.MessageBubble
import com.softistx.material.components.content.PinBar
import com.softistx.material.components.content.QuoteBlock
import com.softistx.material.components.content.Reaction
import com.softistx.material.components.content.ReactionBar
import com.softistx.material.components.content.ReplyPreview
import com.softistx.material.components.content.toggleReaction
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.theme.StxTheme

/** The MessageContent stories of the Content group. */
internal fun StoryGroupBuilder.messageContentStories() {
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
}
