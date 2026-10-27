package com.softistx.material.school.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.content.SuggestionChip
import com.softistx.material.components.form.Composer
import com.softistx.material.foundation.theme.StxTheme

private val QuickReplies = listOf("Thanks!", "I will check and come back to you.", "See you then.")

/** Quick replies above a composer. A suggestion fills the draft; send posts it. */
@Composable
fun ThreadComposer(onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs)) {
            QuickReplies.forEach { SuggestionChip(text = it, onClick = { draft = it }) }
        }
        Composer(
            value = draft,
            onValueChange = { draft = it },
            onSend = {
                onSend(draft.trim())
                draft = ""
            },
            onAttach = {},
        )
    }
}
