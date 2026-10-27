package com.softistx.material

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** WCAG contrast ratio of two opaque colours, 1.0 to 21.0. */
fun contrast(
    a: Color,
    b: Color,
): Double {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}
