package com.strange.material.display

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.ui.unit.dp
import com.strange.material.theme.Tone
import com.strange.material.theme.colors
import com.strange.material.theme.radii
import com.strange.material.theme.spacing

/**
 * The alert borrows the badge's colour pair and adds an outline, so a tone reads the same whether
 * it arrives as a two-word badge or as a paragraph.
 */
fun alertStyle(tone: Tone): Style =
    Style {
        val role = colors.tone(tone)
        background(role.container)
        contentColor(role.onContainer)
        shape(RoundedCornerShape(radii.md))
        border(1.dp, role.main)
        contentPadding(spacing.md)
    }
