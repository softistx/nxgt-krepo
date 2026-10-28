package com.softistx.material.school.messages

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.feedback.StateHost
import com.softistx.material.components.layout.ListDetailLayout
import com.softistx.material.components.layout.ListDetailLayoutDefaults
import com.softistx.material.school.common.ScreenFrame
import com.softistx.material.school.data.Conversations
import com.softistx.material.school.session.DataState
import com.softistx.material.school.session.SchoolSession
import com.softistx.material.school.session.ofList

/** Conversations on the left, the open thread on the right (or one at a time on a phone). */
@Composable
fun MessagesScreen(session: SchoolSession) {
    val open = session.conversationId?.let(Conversations::byId)
    val visible = Conversations.all.filter { it.id !in session.archived }
    ScreenFrame(title = "Messages", session = session, scroll = false) {
        ListDetailLayout(
            selected = open != null,
            modifier = Modifier.weight(1f),
            placeholder = { ListDetailLayoutDefaults.Placeholder("Select a conversation", it) },
            list = { modifier ->
                StateHost(
                    state = session.data.ofList(visible),
                    onRetry = { session.data = DataState.Loaded },
                    modifier = modifier,
                ) { ConversationList(it, session, Modifier.fillMaxSize()) }
            },
            detail = { modifier -> open?.let { ConversationThread(it, session, modifier) } },
        )
    }
}
