package com.strange.material.display

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.strange.material.theme.StrangeTheme

/**
 * A card.
 *
 * ```kotlin
 * Card { Typography("Commande #2481", TypographyVariant.TitleMedium) }
 * ```
 *
 * Passing [onClick] is what makes it interactive, and that single fact decides everything else:
 * the hover response, the press give and the click semantics all turn on together. There is no way
 * to end up with a card that highlights but does nothing, or one that acts but never acknowledges
 * a pointer.
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
    val base = remember(variant, onClick != null) { cardStyle(variant, interactive = onClick != null) }

    Column(
        modifier =
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).styleable(styleState, base, style),
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(StrangeTheme.spacing.sm),
        content = content,
    )
}
