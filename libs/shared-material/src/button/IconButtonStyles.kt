package com.strange.material.button

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.then
import androidx.compose.ui.unit.dp

/**
 * What separates an icon button from a button: a square that stays at least as big as a finger,
 * whatever the icon inside it measures. Composed onto a [buttonStyle] rather than replacing it, so
 * the five variants and seven colours are resolved in exactly one place for both.
 */
val iconButtonTarget: Style =
    Style {
        minWidth(TouchTarget)
        minHeight(TouchTarget)
        contentPadding(8.dp)
    }

/** The full style of an icon button: a button's colours, squared off. */
fun iconButtonStyle(
    variant: ButtonVariant = ButtonVariant.Ghost,
    color: ButtonColor = ButtonColor.Neutral,
): Style = buttonStyle(variant, color) then iconButtonTarget

private val TouchTarget = 40.dp
