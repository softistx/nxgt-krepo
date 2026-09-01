package com.softistx.material.form

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.softistx.material.motion.Transitions
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant

/**
 * The line under a control: its hint, or its complaint when it has one.
 *
 * They share one slot on purpose. A hint and an error stacked together push the next field down
 * the moment something goes wrong, and the reader's eye loses the place it was typing in; swapping
 * them keeps the form still. The error wins while it is showing, and the hint comes back after.
 *
 * The swap is a crossfade on the effects axis — it is a colour and an alpha, and nothing moves.
 */
@Composable
fun HelperText(
    helper: String?,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val message = error ?: helper
    // AnimatedContent's transitionSpec is not a composable scope, so both halves are resolved here.
    val enter = Transitions.fade
    val exit = Transitions.fadeAway
    AnimatedContent(
        targetState = message to (error != null),
        transitionSpec = { enter togetherWith exit },
        modifier = modifier,
        label = "helperText",
    ) { (text, isError) ->
        if (text != null) {
            Typography(
                text = text,
                variant = TypographyVariant.Caption,
                emphasis = if (isError) Emphasis.Full else Emphasis.Medium,
                color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
    }
}
