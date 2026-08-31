package com.strange.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strange.material.icon.Icon
import com.strange.material.icon.IconSize
import com.strange.material.icon.StrangeIcons
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/** One row of a [FeatureList]. */
@Immutable
data class FeatureItem(
    val label: String,
    val included: Boolean = true,
)

/**
 * What a plan includes. Each row is a check or a close, not a [Checkbox] — this is a receipt,
 * not a form.
 *
 * Material 3 has no feature list. The tick is [StrangeIcons.Check] in the success tone; a row
 * that is not included wears the close icon at medium emphasis so it recedes rather than shouts.
 */
@Composable
fun FeatureList(
    items: List<FeatureItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
    ) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
            ) {
                Icon(
                    icon = if (item.included) StrangeIcons.Check else StrangeIcons.Close,
                    description = null,
                    size = IconSize.Small,
                    tint =
                        if (item.included) {
                            StrangeTheme.colors.tone(Tone.Success).main
                        } else {
                            LocalContentColor.current.copy(alpha = Emphasis.Medium.alpha)
                        },
                )
                Typography(
                    text = item.label,
                    emphasis = if (item.included) Emphasis.Full else Emphasis.Medium,
                )
            }
        }
    }
}
