package com.strange.material.demo.stories

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
import com.strange.material.button.Button
import com.strange.material.button.ButtonColor
import com.strange.material.button.ButtonVariant
import com.strange.material.demo.knobs.enumChoice
import com.strange.material.demo.storyGroup
import com.strange.material.display.Card
import com.strange.material.display.ListTile
import com.strange.material.feedback.LoadingMark
import com.strange.material.feedback.Progress
import com.strange.material.feedback.ProgressKind
import com.strange.material.feedback.Toaster
import com.strange.material.feedback.rememberToasterState
import com.strange.material.icon.StrangeIcons
import com.strange.material.surface.Accordion
import com.strange.material.surface.AccordionItem
import com.strange.material.surface.Carousel
import com.strange.material.surface.ConfirmDialog
import com.strange.material.surface.ContextMenu
import com.strange.material.surface.Drawer
import com.strange.material.surface.HoverCard
import com.strange.material.surface.Menu
import com.strange.material.surface.MenuItem
import com.strange.material.surface.Sheet
import com.strange.material.surface.SwipeActions
import com.strange.material.surface.Tooltip
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

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
                    modifier = Modifier.padding(StrangeTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
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
                    Column(Modifier.padding(StrangeTheme.spacing.md)) {
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

        story("Menu") { _ ->
            var open by remember { mutableStateOf(false) }
            Box {
                Button(text = "More", onClick = { open = true }, variant = ButtonVariant.Ghost)
                Menu(
                    expanded = open,
                    onDismiss = { open = false },
                    items =
                        listOf(
                            MenuItem("Edit", onClick = {}, leading = StrangeIcons.Edit),
                            MenuItem("Delete", onClick = {}, leading = StrangeIcons.Delete),
                        ),
                )
            }
        }

        story("Progress") { knobs ->
            val determinate = knobs.flag("Determinate", false)
            val kind = knobs.enumChoice("Kind", ProgressKind.Linear)
            Progress(progress = if (determinate) 0.45f else null, kind = kind)
        }

        story("Loading mark") { knobs ->
            val determinate = knobs.flag("Determinate", false)
            LoadingMark(progress = if (determinate) 0.45f else null)
        }

        story("Toast") { _ ->
            val toaster = rememberToasterState()
            Toaster(state = toaster, modifier = Modifier.height(280.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                    Button(text = "Saved", onClick = { toaster.show("Order saved", Tone.Success) })
                    Button(text = "Warning", onClick = { toaster.show("Payout delayed", Tone.Warning) })
                    Button(
                        text = "Error",
                        onClick = { toaster.show("Could not reach the bank", Tone.Error) },
                    )
                }
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
                        MenuItem("Edit", onClick = {}, leading = StrangeIcons.Edit),
                        MenuItem("Delete", onClick = {}, leading = StrangeIcons.Delete),
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
                            .background(StrangeTheme.colors.tone(Tone.Error).main)
                            .padding(StrangeTheme.spacing.md),
                    ) {
                        Typography(text = "Delete", color = StrangeTheme.colors.tone(Tone.Error).onMain)
                    }
                },
            ) {
                ListTile(title = "Amara Diallo", supporting = "Swipe to reveal")
            }
        }
    }
