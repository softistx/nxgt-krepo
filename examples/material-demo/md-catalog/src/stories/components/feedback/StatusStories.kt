package com.softistx.material.demo.stories.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.feedback.Alert
import com.softistx.material.components.feedback.AlertDefaults
import com.softistx.material.components.feedback.Skeleton
import com.softistx.material.components.feedback.StateHost
import com.softistx.material.components.feedback.StatusBadge
import com.softistx.material.components.feedback.StatusDot
import com.softistx.material.components.feedback.UiState
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/** The Status stories of the Feedback group. */
internal fun StoryGroupBuilder.statusStories() {
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

    story("State host") { knobs ->
        val shown = knobs.enumChoice("State", ShownState.Loading)
        val state: UiState<String> =
            when (shown) {
                ShownState.Loading -> UiState.Loading
                ShownState.Data -> UiState.Data("Three invoices reconciled.")
                ShownState.Error -> UiState.Error("The server did not answer.")
            }
        StateHost(state = state, onRetry = {}) { Typography(text = it) }
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
}

private enum class ShownState { Loading, Data, Error }
