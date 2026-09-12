package com.softistx.material.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.softistx.material.motion.Transitions
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/**
 * What a screen shows when there is nothing to show.
 *
 * ```kotlin
 * EmptyState("Aucune commande", "Les commandes de vos clients apparaîtront ici.")
 * ```
 *
 * It fades in rather than appearing, because an empty state that snaps into place after a load
 * reads as an error. The [action] slot is where the way out goes — an empty state without one is
 * a dead end, and having the slot makes its absence a visible decision.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    illustration: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    AnimatedVisibility(visible = true, enter = Transitions.fade) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    // Generous room, so an empty state reads as deliberate rather than broken.
                    .padding(
                        horizontal = StxTheme.spacing.lg,
                        vertical = StxTheme.spacing.xxl,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
        ) {
            illustration?.invoke()
            Typography(
                text = title,
                variant = TypographyVariant.TitleMedium,
                align = TextAlign.Center,
            )
            if (description != null) {
                Typography(
                    text = description,
                    variant = TypographyVariant.BodyMedium,
                    emphasis = Emphasis.Medium,
                    align = TextAlign.Center,
                )
            }
            action?.invoke()
        }
    }
}
