package com.strange.material.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The elevation scale, named by what a surface *is* rather than by how far it is lifted.
 *
 * Material 3 has no theme-level elevation the way it has `colorScheme`, `typography` and `shapes` —
 * only per-component types like `CardElevation` — so this one is genuinely ours. The **values** are
 * not: each is one of M3's six levels, so a surface raised through this scale sits exactly where a
 * plain M3 component raised through its own `*Defaults` sits.
 *
 * | | dp | M3 level |
 * | --- | --- | --- |
 * | `flat` | 0 | Level 0 |
 * | `raised` | 1 | Level 1 |
 * | `floating` | 3 | Level 2 |
 * | `overlay` | 6 | Level 3 |
 * | `modal` | 12 | Level 5 |
 *
 * Level 4 (8 dp) has no name here because nothing has needed one; M3 itself uses it only for a
 * dragged FAB. Add it with the component that wants it, not before.
 */
@Immutable
data class StrangeElevation(
    val flat: Dp = 0.dp,
    val raised: Dp = 1.dp,
    val floating: Dp = 3.dp,
    val overlay: Dp = 6.dp,
    val modal: Dp = 12.dp,
)
