package com.strange.material.display

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.pressed
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import com.strange.material.motion.MotionSpeed
import com.strange.material.theme.StrangeTheme
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
    val scheme = StrangeTheme.colors.scheme
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

/** Depth from the token scale rather than from M3's per-variant defaults, so the two agree. */
@Composable
fun cardElevation(variant: CardVariant): CardElevation {
    val elevation = StrangeTheme.elevation
    return CardDefaults.cardElevation(
        defaultElevation = if (variant == CardVariant.Elevated) elevation.raised else elevation.flat,
        pressedElevation = if (variant == CardVariant.Elevated) elevation.floating else elevation.flat,
    )
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
