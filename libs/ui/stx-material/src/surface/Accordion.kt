package com.softistx.material.surface

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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.icon.Icon
import com.softistx.material.icon.StxIcons
import com.softistx.material.motion.Transitions
import com.softistx.material.navigation.navigationItemStyle
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/** One section of an [Accordion]. */
@Immutable
data class AccordionItem(
    val title: String,
    val body: String,
)

/**
 * A stack of sections, one of which is open.
 *
 * Material 3 has no accordion. Only [expanded] is open, so a tap cannot leave two sections
 * fighting for height. The chevron is decoration next to the title; the whole row is the target.
 */
@Composable
fun Accordion(
    items: List<AccordionItem>,
    expanded: Int?,
    onExpandedChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            val open = index == expanded
            val interactionSource = remember(index) { MutableInteractionSource() }
            val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = true }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .styleable(styleState, navigationItemStyle, style)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onExpandedChange(if (open) null else index) },
                        ).padding(vertical = StxTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Typography(
                    text = item.title,
                    variant = TypographyVariant.TitleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    icon = if (open) StxIcons.ChevronDown else StxIcons.ChevronRight,
                    description = null,
                )
            }
            AnimatedVisibility(visible = open, enter = Transitions.expand, exit = Transitions.collapse) {
                Typography(
                    text = item.body,
                    modifier = Modifier.padding(bottom = StxTheme.spacing.md),
                )
            }
        }
    }
}
