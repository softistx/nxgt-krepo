package com.softistx.material.text

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

/**
 * Text, named by role rather than dressed by hand.
 *
 * ```kotlin
 * Typography("Commandes", TypographyVariant.TitleLarge)
 * ```
 *
 * The point is that the caller never writes `style = MaterialTheme.typography.titleLarge` — a line
 * that is easy to write wrong and impossible to grep for. [emphasis] handles the other half of the
 * same problem: secondary text is *not* a different size, it is the same size at a lower emphasis,
 * and spelling that as a colour lookup at every call site is how two "muted" greys appear.
 */
@Composable
fun Typography(
    text: String,
    variant: TypographyVariant = TypographyVariant.BodyMedium,
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.Full,
    color: Color = Color.Unspecified,
    align: TextAlign? = null,
    decoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val resolved =
        when {
            color != Color.Unspecified -> color
            else -> LocalContentColor.current.copy(alpha = emphasis.alpha)
        }
    Text(
        text = text,
        modifier = modifier,
        style = variant.style(),
        color = resolved,
        textAlign = align,
        textDecoration = decoration,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * How loudly a piece of text speaks, at a fixed size.
 *
 * Three steps and no more: a fourth would be indistinguishable from its neighbours, and the point
 * of naming them is that two developers reach for the same one.
 */
enum class Emphasis(
    val alpha: Float,
) {
    Full(1f),
    Medium(0.72f),
    Subtle(0.5f),
}
