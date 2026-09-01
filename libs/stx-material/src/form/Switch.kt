package com.softistx.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme
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
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = value,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onValueChange,
                ),
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
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
