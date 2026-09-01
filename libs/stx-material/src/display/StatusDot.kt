package com.softistx.material.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.theme.StxTheme
import com.softistx.material.theme.Tone

/**
 * A presence mark. Material 3 has no equivalent.
 *
 * Eight dp, because it sits *next* to a name rather than on an [com.softistx.material.media.Avatar]
 * — the avatar already has a [Tone] ring for that. Online, busy, away, error: the [tone] is the
 * whole vocabulary.
 */
@Composable
fun StatusDot(
    tone: Tone,
    modifier: Modifier = Modifier,
    description: String? = tone.name.lowercase(),
    size: Dp = 8.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(StxTheme.colors.tone(tone).main)
                .then(
                    if (description != null) {
                        Modifier.semantics { contentDescription = description }
                    } else {
                        Modifier
                    },
                ),
    )
}
