package com.softistx.material.components.media

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.scale
import com.softistx.material.foundation.style.rowPress

/**
 * A clickable image tile of a [Gallery]: a press scale of 0.99, as a card or a row has. A tile is
 * never disabled, so there is no fade; an image does not dim itself, so none is needed.
 */
internal val tileStyle: Style = rowPress
