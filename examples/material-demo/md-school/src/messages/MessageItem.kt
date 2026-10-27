package com.softistx.material.school.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.content.FileChip
import com.softistx.material.components.content.LinkPreview
import com.softistx.material.components.content.MessageBubble
import com.softistx.material.components.datetime.RelativeTime
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.data.Message
import com.softistx.material.school.data.SchoolClock

/** A bubble with whatever travels with it: a file, a link preview, the time. */
@Composable
fun MessageItem(
    message: Message,
    sender: String,
    onReply: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
    ) {
        MessageBubble(
            text = message.text,
            outgoing = message.fromMe,
            name = if (message.fromMe) null else sender,
            onClick = onReply,
        )
        message.attachment?.let { FileChip(name = it, sizeLabel = "84 KB") }
        if ("portal" in message.text) {
            LinkPreview(
                title = "8A project brief",
                url = "portal.school.example/briefs/8a",
                description = "Science fair, due 6 November",
            )
        }
        RelativeTime(at = message.at, now = SchoolClock.now)
    }
}
