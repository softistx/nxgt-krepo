package com.softistx.material.foundation.style

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import com.softistx.material.foundation.motion.MotionSpeed

/**
 * The give under the finger of a full-width part, a card or a row: a scale of 0.99, which is the
 * difference between responding and flinching. The styles of a clickable row, a tile, a card and a
 * toggle row are this one, plus what each adds.
 */
internal val rowPress: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.99f) } }
    }

/** The give of a compact control, a chip, a navigation item or a header: 0.97, since a small target moves more. */
internal val compactPress: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.97f) } }
    }
