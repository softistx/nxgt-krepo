package com.softistx.material.school.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.components.content.LabeledDivider
import com.softistx.material.components.content.MentionChip
import com.softistx.material.components.content.PinBar
import com.softistx.material.components.content.ReplyPreview
import com.softistx.material.components.feedback.TypingIndicator
import com.softistx.material.components.media.AvatarItem
import com.softistx.material.components.media.SeenBy
import com.softistx.material.components.navigation.Breadcrumb
import com.softistx.material.components.navigation.BreadcrumbItem
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.data.Conversation
import com.softistx.material.school.data.Message
import com.softistx.material.school.data.SchoolClock
import com.softistx.material.school.data.Students
import com.softistx.material.school.session.SchoolSession

/** One open conversation: the messages, who has seen them, and the composer. */
@Composable
fun ConversationThread(
    conversation: Conversation,
    session: SchoolSession,
    modifier: Modifier = Modifier,
) {
    val sent = remember(conversation.id) { mutableStateListOf<Message>() }
    var replyTo by remember(conversation.id) { mutableStateOf<Message?>(null) }
    val all = conversation.messages + sent
    Column(modifier = modifier.padding(StxTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        Breadcrumb(
            items = listOf(BreadcrumbItem("Messages") { session.conversationId = null }, BreadcrumbItem(conversation.title)),
        )
        conversation.studentId?.let(Students::byId)?.let { student ->
            MentionChip(name = student.name, onClick = { session.openStudent(student.id) })
        }
        if (conversation.id == 1) PinBar(text = "Retake on Thursday at 12:30 in R101")
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
            item { LabeledDivider(label = "Today") }
            items(all, key = { it.id }) { message ->
                MessageItem(message, conversation.title, onReply = { replyTo = message })
            }
            item {
                if (all.last().fromMe) SeenBy(items = listOf(AvatarItem(conversation.title)), label = null)
                if (conversation.online && !all.last().fromMe) TypingIndicator(label = "${conversation.title} is typing")
            }
        }
        replyTo?.let { ReplyPreview(name = if (it.fromMe) "You" else conversation.title, text = it.text, onDismiss = { replyTo = null }) }
        ThreadComposer(onSend = {
            sent += Message(100 + sent.size, true, it, SchoolClock.now)
            replyTo = null
        })
    }
}
