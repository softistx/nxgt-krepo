package com.softistx.material.components.form

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import androidx.compose.material3.Switch as MaterialSwitch

/**
 * A setting that takes effect as it is flicked.
 *
 * The switch sits at the end of the row and the label at the start, because that is where a
 * settings list puts it and a form of switches is a settings list. The row is the target, as with
 * [Checkbox]; unlike a checkbox it carries no error, since a switch that can be wrong is a
 * checkbox wearing the wrong control.
 */
@Composable
fun Switch(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .styleable(styleState, toggleStyle, style)
                .toggleable(
                    value = value,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    role = Role.Switch,
                    onValueChange = onValueChange,
                ).minimumInteractiveComponentSize(),
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs),
        ) {
            Typography(text = label)
            if (description != null) {
                Typography(
                    text = description,
                    variant = TypographyVariant.Caption,
                    emphasis = Emphasis.Medium,
                )
            }
        }
        MaterialSwitch(checked = value, onCheckedChange = null, enabled = enabled)
    }
}
