package com.softistx.material.components.media

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import com.softistx.material.foundation.motion.MotionSpeed
import com.softistx.material.foundation.style.motion

/**
 * A clickable image tile of a [Gallery]: a press scale of 0.99, as a card or a row has. A tile is
 * never disabled, so there is no fade; an image does not dim itself, so none is needed.
 */
val tileStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.99f) } }
    }
