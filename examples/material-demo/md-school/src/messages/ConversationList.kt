package com.softistx.material.school.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.EmptyStateDefaults
import com.softistx.material.components.content.ListTileDefaults
import com.softistx.material.components.content.ListTileGroup
import com.softistx.material.components.form.InputGroup
import com.softistx.material.components.navigation.Search
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.common.say
import com.softistx.material.school.data.Conversation
import com.softistx.material.school.session.SchoolSession

/** The searchable list of conversations. Swipe a row to archive it. */
@Composable
fun ConversationList(
    conversations: List<Conversation>,
    session: SchoolSession,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val shown = conversations.filter { it.title.contains(text.trim(), ignoreCase = true) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        InputGroup {
            Search(query = text, onQueryChange = { text = it }, placeholder = "Search conversations", modifier = Modifier.weight(1f))
            IconButton(
                icon = StxIcons.Add,
                description = "New message",
                onClick = { session.toaster.say("Pick a guardian from a student's contacts", Tone.Info) },
                variant = ButtonVariant.Tonal,
            )
        }
        when {
            conversations.isEmpty() -> {
                EmptyState(
                    illustration = { EmptyStateDefaults.IconPlate(StxIcons.Inbox) },
                    title = "Inbox zero",
                    description = "No conversation here.",
                    action = {
                        if (session.archived.isNotEmpty()) {
                            Button(text = "Restore archived", onClick = { session.archived.clear() })
                        }
                    },
                )
            }

            shown.isEmpty() -> {
                EmptyState(title = "No conversation matches", description = "Try another name.")
            }

            else -> {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ListTileDefaults.GroupGap)) {
                    itemsIndexed(shown, key = { _, it -> it.id }) { index, it ->
                        ConversationRow(it, session, ListTileGroup(index, shown.size))
                    }
                }
            }
        }
    }
}
