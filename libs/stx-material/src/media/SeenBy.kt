package com.softistx.material.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/** "Sent" when nobody has looked; "Seen by N" otherwise. */
fun seenByLabel(count: Int): String =
    when {
        count <= 0 -> "Sent"
        else -> "Seen by $count"
    }

/**
 * Who has opened a message. An [AvatarGroup] plus a caption.
 *
 * The faces are the group; the words are [seenByLabel] unless the host passes [label]. Material 3
 * has no read-receipt control.
 */
@Composable
fun SeenBy(
    items: List<AvatarItem>,
    modifier: Modifier = Modifier,
    max: Int = 3,
    label: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
    ) {
        if (items.isNotEmpty()) {
            AvatarGroup(items = items, max = max, size = 20.dp)
        }
        Typography(
            text = label ?: seenByLabel(items.size),
            variant = TypographyVariant.Caption,
            emphasis = Emphasis.Medium,
        )
    }
}
