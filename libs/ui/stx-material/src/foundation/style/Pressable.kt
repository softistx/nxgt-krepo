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
 * This modifier dressed by [base] then the caller's [style]. [enabled] is what a `disabled { }`
 * block reads. [interactionSource] is the one the part's own input is wired to; without it the
 * style state has no input behind it, so on a wrapper only static properties and `disabled`
 * apply, and `pressed`, `hovered` and `focused` never fire. [selected] is what a `selected { }`
 * block reads.
 */
@Composable
internal fun Modifier.styledBy(
    style: Style,
    enabled: Boolean = true,
    base: Style = Style,
    interactionSource: MutableInteractionSource? = null,
    selected: Boolean = false,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState =
        rememberUpdatedStyleState(source) {
            it.isEnabled = enabled
            it.isSelected = selected
        }
    return styleable(styleState, base, style)
}

/**
 * The press look [base] when the part is clickable, and nothing when it is not: a plain header or a
 * row with no click does not give under a finger.
 */
internal fun pressBase(
    base: Style,
    onClick: (() -> Unit)?,
): Style = if (onClick != null) base else Style

/**
 * A press target: this modifier, dressed by [base] then the caller's [style], and clickable as a
 * button (labelled [label] when given) when [onClick] is not null. Without it the caller's [style]
 * still applies, since a style is never dropped silently, but the press look ([base]) and the
 * role go: a plain header or a row with no click does not give under a finger.
 */
@Composable
internal fun Modifier.pressable(
    base: Style,
    style: Style,
    onClick: (() -> Unit)?,
    label: String? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val styled = styledBy(style, base = pressBase(base, onClick), interactionSource = interactionSource)
    if (onClick == null) return styled
    return styled.clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        role = Role.Button,
        onClickLabel = label,
        onClick = onClick,
    )
}
