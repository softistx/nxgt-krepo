package com.softistx.material.school.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.Kbd
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.surface.Accordion
import com.softistx.material.components.surface.AccordionItem
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme

private val Questions =
    listOf(
        AccordionItem("How do I correct a mark?", "Open Grades, tap the row, change the value and press Update."),
        AccordionItem("Who sees my messages?", "Only the guardians of the student and the principal."),
        AccordionItem("Can I use the app offline?", "Marks you enter are kept and sent when you are back online."),
    )

/** Three questions, one open at a time. */
@Composable
fun HelpSection() {
    var open by remember { mutableStateOf<Int?>(0) }
    Card {
        SectionHeader(title = "Help")
        Accordion(items = Questions, expanded = open, onExpandedChange = { open = it })
        Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Typography(text = "Jump to the first screen")
            Kbd(keys = listOf("G", "1"))
        }
    }
}
