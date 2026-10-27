package com.softistx.material.school.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.form.CheckItem
import com.softistx.material.components.form.Checklist
import com.softistx.material.components.form.toggleCheckItem

/** The teacher's own to-do list. Ticks are local to the screen. */
@Composable
fun TodoCard() {
    var items by remember {
        mutableStateOf(
            listOf(
                CheckItem("Enter term 1 marks for 7B", checked = true),
                CheckItem("Reply to Claire Garcia"),
                CheckItem("Print the 8A project briefs"),
                CheckItem("Book the lab for Thursday"),
            ),
        )
    }
    Card {
        SectionHeader(title = "To do", supporting = "${items.count { !it.checked }} left")
        Checklist(items = items, onToggle = { items = toggleCheckItem(items, it) })
    }
}
