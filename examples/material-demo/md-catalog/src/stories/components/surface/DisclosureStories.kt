package com.softistx.material.demo.stories.components.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.content.Card
import com.softistx.material.components.surface.Accordion
import com.softistx.material.components.surface.AccordionItem
import com.softistx.material.components.surface.Carousel
import com.softistx.material.components.surface.Disclosure
import com.softistx.material.components.surface.HelpTip
import com.softistx.material.components.surface.Menu
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.components.surface.Tooltip
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography

/** The Disclosure stories of the Surfaces group. */
internal fun StoryGroupBuilder.disclosureStories() {
    story("Tooltip") { _ ->
        Tooltip(text = "Archive this order") {
            Button(text = "Archive", onClick = {}, variant = ButtonVariant.Ghost)
        }
    }

    story("Help tip") { _ ->
        HelpTip(text = "Payouts land the next working day, except on bank holidays.")
    }

    story("Menu") { _ ->
        var open by remember { mutableStateOf(false) }
        Box {
            Button(text = "More", onClick = { open = true }, variant = ButtonVariant.Ghost)
            Menu(
                expanded = open,
                onDismiss = { open = false },
                items =
                    listOf(
                        MenuItem("Edit", onClick = {}, leading = StxIcons.Edit),
                        MenuItem("Delete", onClick = {}, leading = StxIcons.Delete),
                    ),
            )
        }
    }

    story("Disclosure") { _ ->
        var open by remember { mutableStateOf(true) }
        Disclosure(
            title = "Payout schedule",
            expanded = open,
            onExpandedChange = { open = it },
        ) {
            Typography(text = "Payouts land the next working day, except on bank holidays.")
        }
    }

    story("Accordion") { _ ->
        var expanded by remember { mutableStateOf<Int?>(0) }
        Accordion(
            items =
                listOf(
                    AccordionItem("When do I get paid?", "Payouts land the next working day."),
                    AccordionItem("Can I refund a payout?", "Yes, from the order within 14 days."),
                    AccordionItem("Who sees the audit trail?", "Anyone with the Finance role."),
                ),
            expanded = expanded,
            onExpandedChange = { expanded = it },
        )
    }

    story("Carousel") { _ ->
        Carousel(count = 3, modifier = Modifier.fillMaxWidth()) { index ->
            Card(modifier = Modifier.height(140.dp).fillMaxWidth()) {
                Typography(text = "Card ${index + 1}")
                Typography(text = "The next one peeks from the end.")
            }
        }
    }
}
