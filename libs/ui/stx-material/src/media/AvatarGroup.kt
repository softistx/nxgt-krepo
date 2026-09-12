package com.softistx.material.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme
import com.softistx.material.theme.Tone

/** One face in an [AvatarGroup]. */
@Immutable
data class AvatarItem(
    val name: String,
    val image: Any? = null,
    val tone: Tone? = null,
)

/**
 * Faces in a stack, with an overflow count when there are more than [max].
 *
 * Material 3 has no group. Each face is an [Avatar], overlapping by a third of its size so the
 * stack reads as one control rather than a row. The overflow is a matching circle with `+N`, not
 * a badge floating beside them — [initials] would truncate `+12` to `+1`.
 */
@Composable
fun AvatarGroup(
    items: List<AvatarItem>,
    modifier: Modifier = Modifier,
    max: Int = 4,
    size: Dp = 32.dp,
) {
    val shown = items.take(max.coerceAtLeast(0))
    val overflow = (items.size - shown.size).coerceAtLeast(0)
    val overlap = size * 0.7f
    Box(modifier = modifier) {
        shown.forEachIndexed { index, item ->
            Avatar(
                name = item.name,
                image = item.image,
                size = size,
                tone = item.tone,
                modifier = Modifier.padding(start = overlap * index).zIndex(index.toFloat()),
            )
        }
        if (overflow > 0) {
            OverflowMark(
                count = overflow,
                size = size,
                modifier =
                    Modifier
                        .padding(start = overlap * shown.size)
                        .zIndex(shown.size.toFloat()),
            )
        }
    }
}

@Composable
private fun OverflowMark(
    count: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(StxTheme.colors.scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Typography(text = "+$count", variant = TypographyVariant.LabelSmall)
    }
}
