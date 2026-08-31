package com.strange.material.surface

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strange.material.icon.Icon
import com.strange.material.icon.StrangeIcons
import com.strange.material.motion.Transitions
import com.strange.material.navigation.navigationItemStyle
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme

/**
 * One panel that opens. Material 3 has no disclosure.
 *
 * [Accordion] is a *list* with at most one section open. This is a single question, a settings
 * group, a footnote — the chevron is decoration, the whole row is the target.
 */
@Composable
fun Disclosure(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = true }
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .styleable(styleState, navigationItemStyle, style)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onExpandedChange(!expanded) },
                    ).padding(vertical = StrangeTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Typography(
                text = title,
                variant = TypographyVariant.TitleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                icon = if (expanded) StrangeIcons.ChevronDown else StrangeIcons.ChevronRight,
                description = null,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = Transitions.expand,
            exit = Transitions.collapse,
        ) {
            Column(modifier = Modifier.padding(bottom = StrangeTheme.spacing.md)) {
                content()
            }
        }
    }
}
