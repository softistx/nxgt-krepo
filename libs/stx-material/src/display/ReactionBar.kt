package com.softistx.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.softistx.material.theme.StrangeTheme

/** One reaction in a [ReactionBar]. */
@Immutable
data class Reaction(
    val label: String,
    val count: Int,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
)

/** Adds one if it was off, removes one if it was on, never below zero. */
fun toggleReaction(
    count: Int,
    selected: Boolean,
): Pair<Int, Boolean> =
    if (selected) {
        (count - 1).coerceAtLeast(0) to false
    } else {
        count + 1 to true
    }

/**
 * A row of reactions. Each is a [Chip], so selection, the tick and the shape morph are M3's.
 *
 * The label is the emoji or short name; the count sits after it. Toggling is the caller's —
 * [toggleReaction] is the usual arithmetic.
 */
@Composable
fun ReactionBar(
    reactions: List<Reaction>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
    ) {
        reactions.forEach { reaction ->
            Chip(
                text = "${reaction.label} ${reaction.count}",
                selected = reaction.selected,
                onClick = reaction.onClick,
            )
        }
    }
}
