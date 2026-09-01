package com.softistx.material.display

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.softistx.material.theme.StrangeTheme
import androidx.compose.material3.Card as MaterialCard

/**
 * A card. Material 3's, with the padding and the vertical rhythm already applied — M3's cards give
 * you a container and nothing inside it, and every caller then writes the same `Column` with the
 * same padding.
 *
 * A card is interactive **iff** [onClick] is not null: only then does it take a ripple, a pressed
 * elevation and the press scale. M3 has a separate clickable overload for exactly this, so the two
 * cases are two calls rather than a `Modifier.clickable` bolted on.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Filled,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: Style = Style,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    val styled = modifier.styleable(styleState, cardStyle, style)
    val colors = cardColors(variant)
    val elevation = cardElevation(variant)
    val shape = MaterialTheme.shapes.large
    val border = CardDefaults.outlinedCardBorder(enabled)
    val padded: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(StrangeTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
            content = content,
        )
    }

    when {
        onClick == null && variant == CardVariant.Outlined -> {
            OutlinedCard(styled, shape, colors, elevation, content = padded)
        }

        onClick == null && variant == CardVariant.Elevated -> {
            ElevatedCard(styled, shape, colors, elevation, content = padded)
        }

        onClick == null -> {
            MaterialCard(styled, shape, colors, elevation, content = padded)
        }

        variant == CardVariant.Outlined -> {
            OutlinedCard(onClick, styled, enabled, shape, colors, elevation, border, interactionSource, padded)
        }

        variant == CardVariant.Elevated -> {
            ElevatedCard(onClick, styled, enabled, shape, colors, elevation, interactionSource, padded)
        }

        else -> {
            MaterialCard(onClick, styled, enabled, shape, colors, elevation, null, interactionSource, padded)
        }
    }
}
