package com.strange.material.navigation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.strange.material.text.Typography

/**
 * A single choice among a short, closed set. Material 3's `SingleChoiceSegmentedButtonRow`.
 *
 * It is not a [com.strange.material.button.ButtonRow] (a layout) and not [Tabs] (a destination).
 * Three to five options that partition one view — "Day / Week / Month" — is the size it is for.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.styleable(styleState, navigationItemStyle, style),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                enabled = enabled,
                label = { Typography(text = option) },
            )
        }
    }
}
