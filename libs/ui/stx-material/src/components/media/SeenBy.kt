package com.softistx.material.components.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.LocalStxStrings
import com.softistx.material.foundation.theme.StxTheme

/** "Sent" when nobody has looked; "Seen by N" otherwise, in the language of [strings]. */
fun seenByLabel(
    count: Int,
    strings: StxStrings = StxStrings.En,
): String =
    when {
        count <= 0 -> strings.sent
        else -> strings.seenBy(count)
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
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
    ) {
        if (items.isNotEmpty()) {
            AvatarGroup(items = items, max = max, size = 20.dp)
        }
        Typography(
            text = label ?: seenByLabel(items.size, LocalStxStrings.current),
            variant = TypographyVariant.Caption,
            emphasis = Emphasis.Medium,
        )
    }
}
