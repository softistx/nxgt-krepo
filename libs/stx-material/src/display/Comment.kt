package com.softistx.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.media.Avatar
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * A named paragraph: face, author, body, and an optional trailing slot.
 *
 * A [ListTile] is a row in a list; a [MessageBubble] is a chat turn. This is the comment under an
 * article or a task — the body wraps, [supporting] is the already-formatted time, and [trailing]
 * is usually a [ReactionBar] or a [com.softistx.material.button.MoreMenu].
 */
@Composable
fun Comment(
    name: String,
    text: String,
    modifier: Modifier = Modifier,
    image: Any? = null,
    supporting: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
    ) {
        Avatar(name = name, image = image, size = 32.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
            ) {
                Typography(text = name, variant = TypographyVariant.TitleSmall)
                if (supporting != null) {
                    Typography(
                        text = supporting,
                        variant = TypographyVariant.Caption,
                        emphasis = Emphasis.Medium,
                    )
                }
            }
            Typography(text = text)
            trailing?.invoke()
        }
    }
}
