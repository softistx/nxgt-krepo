package com.softistx.material.demo.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonColor
import com.softistx.material.button.ButtonVariant
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.demo.storyGroup
import com.softistx.material.display.Card
import com.softistx.material.display.ListTile
import com.softistx.material.feedback.LabeledProgress
import com.softistx.material.feedback.LoadingMark
import com.softistx.material.feedback.Progress
import com.softistx.material.feedback.ProgressKind
import com.softistx.material.feedback.Toaster
import com.softistx.material.feedback.TypingIndicator
import com.softistx.material.feedback.rememberToasterState
import com.softistx.material.icon.StxIcons
import com.softistx.material.surface.Accordion
import com.softistx.material.surface.AccordionItem
import com.softistx.material.surface.Carousel
import com.softistx.material.surface.ConfirmDialog
import com.softistx.material.surface.ContextMenu
import com.softistx.material.surface.Disclosure
import com.softistx.material.surface.Drawer
import com.softistx.material.surface.HelpTip
import com.softistx.material.surface.HoverCard
import com.softistx.material.surface.Menu
import com.softistx.material.surface.MenuItem
import com.softistx.material.surface.Sheet
import com.softistx.material.surface.SwipeActions
import com.softistx.material.surface.Tooltip
import com.softistx.material.text.Typography
import com.softistx.material.theme.StxTheme
import com.softistx.material.theme.Tone

val SurfaceStories =
    storyGroup("Surfaces") {
        story("Confirm dialog") { knobs ->
            var visible by remember { mutableStateOf(false) }
            Button(text = "Delete order", onClick = { visible = true }, color = ButtonColor.Danger)
            ConfirmDialog(
                visible = visible,
                title = "Delete this order?",
                text = "The payout will be reversed. This cannot be undone.",
                onConfirm = { visible = false },
                onDismiss = { visible = false },
                destructive = knobs.flag("Destructive", true),
            )
        }

        story("Sheet") { _ ->
            var visible by remember { mutableStateOf(false) }
            Button(text = "Filters", onClick = { visible = true })
            Sheet(visible = visible, onDismiss = { visible = false }) {
                Column(
                    modifier = Modifier.padding(StxTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
                ) {
                    Typography(text = "Status")
                    Button(text = "Paid", onClick = { visible = false }, variant = ButtonVariant.Ghost)
                    Button(text = "Pending", onClick = { visible = false }, variant = ButtonVariant.Ghost)
                }
            }
        }

        story("Drawer") { _ ->
            var open by remember { mutableStateOf(false) }
            Drawer(
                open = open,
                onDismiss = { open = false },
                modifier = Modifier.height(360.dp),
                drawer = {
                    Column(Modifier.padding(StxTheme.spacing.md)) {
                        ListTile(title = "Inbox", onClick = { open = false })
                        ListTile(title = "People", onClick = { open = false })
                    }
                },
            ) {
                Button(text = "Open drawer", onClick = { open = true })
            }
        }

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

        story("Progress") { knobs ->
            val determinate = knobs.flag("Determinate", false)
            val kind = knobs.enumChoice("Kind", ProgressKind.Linear)
            Progress(progress = if (determinate) 0.45f else null, kind = kind)
        }

        story("Labeled progress") { knobs ->
            val determinate = knobs.flag("Determinate", true)
            LabeledProgress(
                progress = if (determinate) 0.45f else null,
                caption = "Uploading invoice.pdf",
                kind = knobs.enumChoice("Kind", ProgressKind.Linear),
            )
        }

        story("Loading mark") { knobs ->
            val determinate = knobs.flag("Determinate", false)
            LoadingMark(progress = if (determinate) 0.45f else null)
        }

        story("Typing indicator") { knobs ->
            TypingIndicator(label = knobs.text("Label", "Amara is typing"))
        }

        story("Toast") { _ ->
            val toaster = rememberToasterState()
            Toaster(state = toaster, modifier = Modifier.height(280.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                    Button(text = "Saved", onClick = { toaster.show("Order saved", Tone.Success) })
                    Button(text = "Warning", onClick = { toaster.show("Payout delayed", Tone.Warning) })
                    Button(
                        text = "Error",
                        onClick = { toaster.show("Could not reach the bank", Tone.Error) },
                    )
                }
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

        story("Hover card") { _ ->
            HoverCard(
                title = "Amara Diallo",
                text = "Customer since 2024. Last order this morning.",
                action = "Open",
                onAction = {},
            ) {
                Button(text = "Amara Diallo", onClick = {}, variant = ButtonVariant.Ghost)
            }
        }

        story("Context menu") { _ ->
            ContextMenu(
                items =
                    listOf(
                        MenuItem("Edit", onClick = {}, leading = StxIcons.Edit),
                        MenuItem("Delete", onClick = {}, leading = StxIcons.Delete),
                    ),
            ) {
                ListTile(title = "Right-click or long-press", supporting = "Opens the menu at the pointer")
            }
        }

        story("Swipe actions") { _ ->
            SwipeActions(
                onDismiss = {},
                background = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(StxTheme.colors.tone(Tone.Error).main)
                            .padding(StxTheme.spacing.md),
                    ) {
                        Typography(text = "Delete", color = StxTheme.colors.tone(Tone.Error).onMain)
                    }
                },
            ) {
                ListTile(title = "Amara Diallo", supporting = "Swipe to reveal")
            }
        }
    }
