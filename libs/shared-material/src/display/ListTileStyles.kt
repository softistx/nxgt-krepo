package com.strange.material.display

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.pressed
import com.strange.material.motion.MotionSpeed
import com.strange.material.theme.motion

/**
 * The press. Height, padding and colours are `ListItem`'s — the 56 dp two-line height this file
 * used to state as a constant is one of the things M3 was already getting right.
 */
val listTileStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.99f) } }
    }
