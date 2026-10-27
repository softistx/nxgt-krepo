package com.softistx.material.demo.stories.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.feedback.Alert
import com.softistx.material.components.feedback.AlertDefaults
import com.softistx.material.components.feedback.AnnouncementBar
import com.softistx.material.components.feedback.LabeledProgress
import com.softistx.material.components.feedback.LoadingMark
import com.softistx.material.components.feedback.Progress
import com.softistx.material.components.feedback.ProgressKind
import com.softistx.material.components.feedback.PromoBanner
import com.softistx.material.components.feedback.Skeleton
import com.softistx.material.components.feedback.StatusBadge
import com.softistx.material.components.feedback.StatusDot
import com.softistx.material.components.feedback.Toaster
import com.softistx.material.components.feedback.TypingIndicator
import com.softistx.material.components.feedback.rememberToasterState
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.demo.storyGroup
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

val FeedbackStories =
    storyGroup("Feedback") {
        story("Status badge") { _ ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                Tone.entries.forEach { StatusBadge(text = it.name.lowercase(), tone = it) }
            }
        }

        story("Alert") { knobs ->
            val tone = knobs.enumChoice("Tone", Tone.Warning)
            Alert(
                text = knobs.text("Text", "Two invoices could not be reconciled."),
                tone = tone,
                title = if (knobs.flag("Title", true)) "Reconciliation paused" else null,
                visible = knobs.flag("Visible", true),
                icon = if (knobs.flag("Icon", true)) AlertDefaults.icon(tone) else null,
                action = { Button(text = "Review", onClick = {}, variant = ButtonVariant.Link) },
            )
        }

        story("Skeleton") { knobs ->
            val rows = knobs.number("Rows", 3f, 1f..6f, steps = 4).toInt()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
            ) {
                Skeleton(height = 24.dp, modifier = Modifier.fillMaxWidth(0.4f))
                repeat(rows) { Skeleton() }
            }
        }

        story("Status dot") { _ ->
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                Tone.entries.forEach { tone ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(tone = tone)
                        Typography(text = tone.name.lowercase())
                    }
                }
            }
        }

        story("Announcement bar") { knobs ->
            val tone = knobs.enumChoice("Tone", Tone.Warning)
            var visible by remember { mutableStateOf(true) }
            if (visible) {
                AnnouncementBar(
                    text = knobs.text("Text", "The bank is delayed until Tuesday."),
                    tone = tone,
                    icon = if (knobs.flag("Icon", true)) AlertDefaults.icon(tone) else null,
                    onDismiss = { visible = false },
                    action = { Button(text = "Details", onClick = {}, variant = ButtonVariant.Link) },
                )
            } else {
                Button(text = "Show again", onClick = { visible = true }, variant = ButtonVariant.Ghost)
            }
        }

        story("Promo banner") { knobs ->
            val tone = knobs.enumChoice("Tone", Tone.Success)
            var visible by remember { mutableStateOf(true) }
            if (visible) {
                PromoBanner(
                    text = knobs.text("Text", "Spring sale — 20% off the first year."),
                    code = knobs.text("Code", "SPRING20").ifBlank { null },
                    tone = tone,
                    icon = if (knobs.flag("Icon", true)) AlertDefaults.icon(tone) else null,
                    onDismiss = { visible = false },
                )
            } else {
                Button(text = "Show again", onClick = { visible = true }, variant = ButtonVariant.Ghost)
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
    }
