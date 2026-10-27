package com.softistx.material.components.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ToggleButton as MaterialToggleButton

/**
 * One choice among a few, as Material 3's connected button group: a row of toggle buttons that
 * touch, squared where they meet and rounded at the two ends, each morphing under the press.
 *
 * Material 3 has no group composable in this version, so this is the `Row` it documents, spaced by
 * `ButtonGroupDefaults.ConnectedSpaceBetween`. Exactly one item is checked and none shows a
 * checkmark: the filled container is the selection. The segments share the widest one's width, or the
 * whole width when the caller fills it. Each item announces `Role.RadioButton` and
 * `selected`, not the checkbox a bare `ToggleButton` says, and the row is a `selectableGroup`.
 *
 * It is the body of [com.softistx.material.components.navigation.SegmentedControl] and [ViewToggle].
 * [style] reaches every item, and a morphing control takes no press scale of its own. The segment
 * colours come from M3's `toggleButtonColors` and are not themeable by [style].
 */
@Composable
internal fun ConnectedToggleGroup(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: Style = Style,
    item: @Composable RowScope.(index: Int) -> Unit,
) {
    Row(
        modifier = modifier.width(IntrinsicSize.Max).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val interactionSource = remember { MutableInteractionSource() }
            val checked = index == selected
            val styleState =
                rememberUpdatedStyleState(interactionSource) {
                    it.isEnabled = enabled
                    it.isSelected = checked
                }
            val scheme = MaterialTheme.colorScheme
            MaterialToggleButton(
                checked = checked,
                onCheckedChange = { if (it) onSelect(index) },
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.RadioButton
                            this.selected = checked
                        }.styleable(styleState, buttonStyle, style),
                enabled = enabled,
                shapes = connectedShapes(index, count),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                        checkedContainerColor = scheme.primary,
                        checkedContentColor = scheme.onPrimary,
                    ),
                interactionSource = interactionSource,
            ) { item(index) }
        }
    }
}

@Composable
private fun connectedShapes(
    index: Int,
    count: Int,
) = when {
    count == 1 -> ToggleButtonDefaults.shapes()
    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    index == count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}
