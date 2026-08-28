package com.strange.material.display

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.ui.unit.dp
import com.strange.material.theme.motion
import com.strange.material.theme.radii
import com.strange.material.theme.scheme
import com.strange.material.theme.spacing

/** How a card separates itself from the page behind it. */
enum class CardVariant {
    /** A tinted surface. The default, and the quietest thing that still reads as a card. */
    Filled,

    /** A border and no fill, for a dense grid where fills would fight. */
    Outlined,

    /** Lifted off the page. For something that floats above the content around it. */
    Elevated,
}

/**
 * A card's look, including what it does under a pointer.
 *
 * [interactive] is the whole reason this takes an argument: a card that can be clicked must say so
 * on hover, and a card that cannot must stay perfectly still. Getting that backwards is the most
 * common way a list of cards feels broken, so it is a parameter rather than a guess.
 */
fun cardStyle(
    variant: CardVariant = CardVariant.Filled,
    interactive: Boolean = false,
): Style =
    Style {
        shape(RoundedCornerShape(radii.lg))
        contentPadding(spacing.md)

        when (variant) {
            CardVariant.Filled -> {
                background(scheme.surfaceContainer)
            }

            CardVariant.Outlined -> {
                background(scheme.surface)
                border(1.dp, scheme.outlineVariant)
            }

            CardVariant.Elevated -> {
                background(scheme.surfaceContainerLow)
                dropShadow(
                    androidx.compose.ui.graphics.shadow
                        .Shadow(radius = 8.dp),
                )
            }
        }

        if (interactive) {
            hovered { animate { background(scheme.surfaceContainerHigh) } }
            pressed { animate(motion.spec(motion.instant)) { scale(0.99f) } }
        }
    }
