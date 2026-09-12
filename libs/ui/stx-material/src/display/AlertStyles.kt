package com.softistx.material.display

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.border
import androidx.compose.foundation.style.contentPadding
import androidx.compose.ui.unit.dp
import com.softistx.material.theme.Tone
import com.softistx.material.theme.colors
import com.softistx.material.theme.shapes
import com.softistx.material.theme.spacing

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
