package com.strange.material.display

import androidx.compose.foundation.style.Style
import androidx.compose.ui.unit.dp
import com.strange.material.theme.Tone
import com.strange.material.theme.colors
import com.strange.material.theme.shapes
import com.strange.material.theme.spacing

/**
 * Material 3 has no alert or banner, so this one is built from primitives — see AGENTS.md's
 * *Building a component* for when that is the right answer.
 *
 * The alert borrows the badge's colour pair and adds an outline, so a tone reads the same whether
 * it arrives as a two-word badge or as a paragraph.
 */
fun alertStyle(tone: Tone): Style =
    Style {
        val role = colors.tone(tone)
        background(role.container)
        contentColor(role.onContainer)
        shape(shapes.medium)
        border(1.dp, role.main)
        contentPadding(spacing.md)
    }
