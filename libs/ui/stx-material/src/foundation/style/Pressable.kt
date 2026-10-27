package com.softistx.material.foundation.style

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * This modifier dressed by [style] alone, for a part whose look is Material 3's own and whose
 * caller may still want to add to it: there is no default look to restate. [enabled] is what a
 * `disabled { }` block in [style] reads.
 */
@Composable
internal fun Modifier.styledBy(
    style: Style,
    enabled: Boolean = true,
): Modifier {
    val styleState = rememberUpdatedStyleState(remember { MutableInteractionSource() }) { it.isEnabled = enabled }
    return styleable(styleState, style)
}

/**
 * A press target: this modifier, dressed by [base] then the caller's [style], and clickable as a
 * button (labelled [label] when given) when [onClick] is not null. Without it the modifier is
 * unchanged, so a plain header or a row with no click carries no style, no press and no role.
 */
@Composable
internal fun Modifier.pressable(
    base: Style,
    style: Style,
    onClick: (() -> Unit)?,
    label: String? = null,
): Modifier {
    if (onClick == null) return this
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = true }
    return styleable(styleState, base, style).clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        role = Role.Button,
        onClickLabel = label,
        onClick = onClick,
    )
}
