package com.strange.material.display

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.strange.material.motion.MotionSpeed
import com.strange.material.theme.motion

/** Which of Material 3's three cards this is. */
enum class CardVariant {
    /** A tinted container. The quiet default. */
    Filled,

    /** A hairline instead of a fill, for a card inside an already-tinted surface. */
    Outlined,

    /** Lifted off the page. The one to spend when a card is genuinely above its neighbours. */
    Elevated,
}

/** The container colours, as Material 3's own [CardColors], so M3 does the painting. */
@Composable
fun cardColors(variant: CardVariant): CardColors {
    val scheme = MaterialTheme.colorScheme
    return when (variant) {
        CardVariant.Filled -> {
            CardDefaults.cardColors(
                containerColor = scheme.surfaceContainer,
                contentColor = scheme.onSurface,
            )
        }

        CardVariant.Outlined -> {
            CardDefaults.outlinedCardColors(
                containerColor = scheme.surface,
                contentColor = scheme.onSurface,
            )
        }

        CardVariant.Elevated -> {
            CardDefaults.elevatedCardColors(
                containerColor = scheme.surfaceContainerLow,
                contentColor = scheme.onSurface,
            )
        }
    }
}

/**
 * Depth, straight from Material 3.
 *
 * Each variant asks for the `CardElevation` M3 already defines for it, so a card here sits exactly
 * where a plain M3 card of the same kind sits. There is no elevation scale of our own to keep in
 * step: M3 names these levels, and naming them a second time only creates somewhere for the two to
 * disagree.
 */
@Composable
fun cardElevation(variant: CardVariant): CardElevation =
    when (variant) {
        CardVariant.Filled -> CardDefaults.cardElevation()
        CardVariant.Outlined -> CardDefaults.outlinedCardElevation()
        CardVariant.Elevated -> CardDefaults.elevatedCardElevation()
    }

/**
 * The give under the finger, which Material 3 has no parameter for. A card is bigger than a button,
 * so it moves less — 0.99 rather than 0.97, which is the difference between responding and
 * flinching.
 */
val cardStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.99f) } }
    }
