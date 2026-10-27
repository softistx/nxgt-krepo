package com.softistx.material.school.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.datetime.RelativeTime
import com.softistx.material.components.feedback.StatusBadge
import com.softistx.material.components.media.Avatar
import com.softistx.material.components.surface.SwipeActions
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.common.say
import com.softistx.material.school.data.Conversation
import com.softistx.material.school.data.SchoolClock
import com.softistx.material.school.session.SchoolSession

/** A conversation row: avatar with a presence ring, last message, time and unread count. */
@Composable
fun ConversationRow(
    conversation: Conversation,
    session: SchoolSession,
) {
    SwipeActions(
        onDismiss = {
            session.archived += conversation.id
            session.toaster.say("Archived ${conversation.title}", Tone.Info)
        },
        background = {
            Typography(text = "Archive", modifier = Modifier.padding(StxTheme.spacing.md))
        },
    ) {
        ListTile(
            title = conversation.title,
            supporting = conversation.last.text,
            onClick = { session.conversationId = conversation.id },
            modifier = Modifier.weight(1f),
            leading = { Avatar(name = conversation.title, tone = if (conversation.online) Tone.Success else null) },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    RelativeTime(at = conversation.last.at, now = SchoolClock.now)
                    if (conversation.unread > 0) StatusBadge(text = "${conversation.unread} new", tone = Tone.Info)
                }
            },
        )
    }
}
