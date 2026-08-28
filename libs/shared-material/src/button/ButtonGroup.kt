package com.strange.material.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strange.material.theme.StrangeTheme

/**
 * Buttons that belong together, spaced consistently.
 *
 * ```kotlin
 * ButtonGroup {
 *     Button("Annuler", onClick = ::dismiss, variant = ButtonVariant.Ghost)
 *     Button("Enregistrer", onClick = ::save)
 * }
 * ```
 *
 * Trivial, and worth having: without it every dialog footer in a product picks its own gap and its
 * own alignment, and they never quite match. [align] defaults to trailing because that is where a
 * form's actions belong.
 */
@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    align: Alignment.Horizontal = Alignment.End,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm, align),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
