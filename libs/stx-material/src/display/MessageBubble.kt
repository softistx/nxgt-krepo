package com.softistx.material.display

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.media.Avatar
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme
import androidx.compose.material3.Card as MaterialCard

/**
 * One message in a thread. Material 3's `Card`, coloured and aligned by who sent it.
 *
 * Incoming sits on the start edge in the filled card colour; outgoing sits on the end edge in
 * `primaryContainer`. [name] grows an [Avatar]; [meta] is the timestamp or "Read" the host already
 * formatted. A [ListTile] is a row in a list; this is the bubble.
 */
@Composable
fun MessageBubble(
    text: String,
    modifier: Modifier = Modifier,
    outgoing: Boolean = false,
    name: String? = null,
    image: Any? = null,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = true }
    val scheme = MaterialTheme.colorScheme
    val colors =
        if (outgoing) {
            CardDefaults.cardColors(
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
            )
        } else {
            cardColors(CardVariant.Filled)
        }
    val shape = MaterialTheme.shapes.large
    val elevation = cardElevation(CardVariant.Filled)
    val padded: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(StrangeTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
        ) {
            if (!outgoing && name != null) {
                Typography(text = name, variant = TypographyVariant.LabelSmall, emphasis = Emphasis.Medium)
            }
            Typography(text = text)
            if (meta != null) {
                Typography(text = meta, variant = TypographyVariant.Caption, emphasis = Emphasis.Medium)
            }
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier.widthIn(max = 320.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
        ) {
            if (!outgoing && name != null) {
                Avatar(name = name, image = image, size = 32.dp)
            }
            val bubble =
                Modifier
                    .weight(1f, fill = false)
                    .styleable(styleState, if (onClick != null) cardStyle else Style, style)
            if (onClick == null) {
                MaterialCard(bubble, shape, colors, elevation, content = { padded() })
            } else {
                MaterialCard(
                    onClick,
                    bubble,
                    true,
                    shape,
                    colors,
                    elevation,
                    null,
                    interactionSource,
                ) { padded() }
            }
            if (outgoing && name != null) {
                Avatar(name = name, image = image, size = 32.dp)
            }
        }
    }
}
