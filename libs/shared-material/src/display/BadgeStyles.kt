package com.strange.material.display

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import com.strange.material.theme.Tone
import com.strange.material.theme.colors
import com.strange.material.theme.radii
import com.strange.material.theme.spacing

/** A badge is a container colour and a pill. Nothing about it reacts, so it declares no states. */
fun badgeStyle(tone: Tone): Style =
    Style {
        val role = colors.tone(tone)
        background(role.container)
        contentColor(role.onContainer)
        shape(RoundedCornerShape(radii.full))
        contentPaddingHorizontal(spacing.sm)
        contentPaddingVertical(spacing.xxs)
    }
