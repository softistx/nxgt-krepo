package com.softistx.material.components.feedback

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import com.softistx.material.foundation.style.colors
import com.softistx.material.foundation.style.scheme
import com.softistx.material.foundation.style.shapes
import com.softistx.material.foundation.style.spacing
import com.softistx.material.foundation.theme.Tone

/**
 * Material 3 has no alert or banner, so this one is built from primitives — see AGENTS.md's
 * *Building a component* for when that is the right answer.
 *
 * A tonal surface with no outline: the tone lives on the [TonePlate] at the start, so a screen with
 * two notices spends two small spots of colour rather than two slabs. Only an error earns a
 * coloured container, because it is the one tone that must interrupt.
 */
fun alertStyle(tone: Tone): Style =
    Style {
        if (tone == Tone.Error) {
            background(colors.tone(Tone.Error).container)
            contentColor(colors.tone(Tone.Error).onContainer)
        } else {
            background(scheme.surfaceContainerHigh)
            contentColor(scheme.onSurface)
        }
        shape(shapes.largeIncreased)
        contentPadding(spacing.md)
    }
