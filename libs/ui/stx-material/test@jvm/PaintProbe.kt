package com.softistx.material

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.ui.graphics.Color

/** A style that paints the node Red, so a spec can see whether a `style` parameter reached it. */
val painted = Style { background(Color.Red) }

/** Red only while the node is disabled. */
val paintedWhenDisabled = Style { disabled { background(Color.Red) } }

/** A painted red pixel has no green; a press ripple darkens the red (down to about 0.4) but never adds green. */
fun Color.isPainted(): Boolean = red > 0.3f && green < 0.1f

/** A pixel the style left uncovered: still near white. */
fun Color.isBare(): Boolean = green > 0.9f
