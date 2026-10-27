package com.softistx.material.components.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.softistx.material.foundation.motion.Transitions
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * What a screen shows when there is nothing to show.
 *
 * ```kotlin
 * EmptyState("Aucune commande", "Les commandes de vos clients apparaîtront ici.")
 * ```
 *
 * [icon] draws a `MaterialShapes.SoftBurst` plate above the title (an [illustration] replaces it, and
 * passing both is refused). The title is emphasised and announced as a heading.
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
    icon: ImageVector? = null,
) {
    require(illustration == null || icon == null) {
        "EmptyState: illustration and icon both set; the icon plate is the default picture, pass one of them"
    }
    // Starts hidden and targets visible: `AnimatedVisibility(visible = true)` is already shown on
    // its first composition, so its enter transition never runs.
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = shown, enter = Transitions.fade) {
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
            if (icon != null) IconPlate(icon)
            Typography(
                text = title,
                variant = TypographyVariant.TitleLarge,
                emphasized = true,
                modifier = Modifier.semantics { heading() },
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
