package com.strange.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.button.Button
import com.strange.material.button.ButtonVariant
import com.strange.material.display.Alert
import com.strange.material.display.Card
import com.strange.material.display.Chip
import com.strange.material.display.EmptyState
import com.strange.material.display.ListTile
import com.strange.material.display.Skeleton
import com.strange.material.display.StatusBadge
import com.strange.material.motion.animateStagger
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/**
 * The acceptance criterion of every phase, written as a screen.
 *
 * Read it for what is *absent*: no `animate*AsState`, no transition, no interaction source, no
 * remembered hover or press or visibility. The one `remember` is the selected filter, which is the
 * screen's own business state and the only thing here a real application would also own. If a
 * future phase makes this file need plumbing, that phase is not finished.
 */
@Composable
fun OrdersScreen(
    state: ScreenState,
    alert: Boolean,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(SampleFilters.first()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Typography(
                text = "Orders",
                variant = TypographyVariant.HeadlineSmall,
                modifier = Modifier.weight(1f),
            )
            Button(text = "New order", onClick = {})
        }

        Alert(
            text = "Two payouts could not be reconciled with the bank statement.",
            tone = Tone.Warning,
            title = "Reconciliation paused",
            visible = alert,
            action = { Button(text = "Review", onClick = {}, variant = ButtonVariant.Link) },
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
            SampleFilters.forEach { name ->
                Chip(text = name, selected = name == filter, onClick = { filter = name })
            }
        }

        when (state) {
            ScreenState.Loading -> {
                repeat(4) { index ->
                    Card(modifier = Modifier.animateStagger(index)) {
                        Skeleton(height = 20.dp, modifier = Modifier.fillMaxWidth(0.45f))
                        Skeleton()
                    }
                }
            }

            ScreenState.Empty -> {
                EmptyState(
                    title = "Nothing matches “$filter”",
                    description = "Orders show up here the moment a payment is captured.",
                    action = { Button(text = "Clear filter", onClick = { filter = SampleFilters.first() }) },
                )
            }

            ScreenState.Loaded -> {
                SampleOrders.forEachIndexed { index, order ->
                    Card(modifier = Modifier.animateStagger(index), onClick = {}) {
                        ListTile(
                            title = order.customer,
                            supporting = order.reference,
                            trailing = { StatusBadge(text = order.status, tone = order.tone) },
                        )
                    }
                }
            }
        }
    }
}

enum class ScreenState { Loading, Loaded, Empty }
