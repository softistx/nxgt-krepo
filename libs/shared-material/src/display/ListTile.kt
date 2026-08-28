package com.strange.material.display

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme

/**
 * A row in a list: something on the left, two lines of text, something on the right.
 *
 * ```kotlin
 * ListTile("Marie Dupont", supporting = "marie@example.com")
 * ```
 *
 * [leading] and [trailing] are composable slots rather than an icon name and a tint, which is what
 * keeps the component from becoming a dead end the first time a caller needs an avatar, a
 * checkbox or a badge there.
 */
@Composable
fun ListTile(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: Style = Style,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    val base = remember(onClick != null) { listTileStyle(interactive = onClick != null) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
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
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
        ) {
            Typography(text = title, variant = TypographyVariant.BodyLarge)
            if (supporting != null) {
                Typography(
                    text = supporting,
                    variant = TypographyVariant.BodySmall,
                    emphasis = Emphasis.Medium,
                )
            }
        }
        trailing?.invoke()
    }
}
