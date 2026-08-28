package com.strange.material.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The elevation scale, named by what a surface *is* rather than by how far it is lifted.
 *
 * Material 3 expresses depth mostly through surface tone, so these stay small: they exist to give
 * a component one name to reach for, and to keep a raised card and a raised button agreeing.
 */
@Immutable
data class StrangeElevation(
    val flat: Dp = 0.dp,
    val raised: Dp = 1.dp,
    val floating: Dp = 3.dp,
    val overlay: Dp = 6.dp,
    val modal: Dp = 12.dp,
)
