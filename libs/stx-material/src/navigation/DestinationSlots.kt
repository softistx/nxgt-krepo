package com.softistx.material.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.display.Chip
import com.softistx.material.display.StatusBadge
import com.softistx.material.icon.Icon
import com.softistx.material.icon.IconSize
import com.softistx.material.media.Avatar
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

@Composable
internal fun DestinationLeading(
    destination: NavigationDestination,
    selected: Boolean,
) {
    val icon = @Composable {
        if (destination.usesAvatar()) {
            Avatar(
                name = destination.label,
                image = destination.picture,
                size = 24.dp,
                description = null,
            )
        } else {
            val vector =
                if (selected) {
                    destination.selectedIcon ?: destination.icon
                } else {
                    destination.icon
                }
            val tone = destination.tone
            if (tone != null) {
                Icon(
                    icon = vector,
                    description = null,
                    tint = StxTheme.colors.tone(tone).main,
                )
            } else {
                Icon(icon = vector, description = null)
            }
        }
    }
    if (!destination.busy) {
        icon()
        return
    }
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(IconSize.Medium.dp)) { icon() }
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
        )
    }
}

@Composable
internal fun DestinationLabel(
    destination: NavigationDestination,
    compact: Boolean,
) {
    val chip = destination.showChip(compact)
    val shortcut = destination.showShortcut(compact)
    val supporting = destination.showSupporting(compact)
    if (!chip && !shortcut && !supporting) {
        Typography(text = destination.label)
        return
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
        ) {
            Typography(text = destination.label)
            if (chip) Chip(text = destination.chip!!)
            if (shortcut) {
                Typography(
                    text = destination.shortcut!!,
                    variant = TypographyVariant.LabelSmall,
                    emphasis = Emphasis.Subtle,
                )
            }
        }
        if (supporting) {
            Typography(
                text = destination.supporting!!,
                variant = TypographyVariant.BodySmall,
                emphasis = Emphasis.Subtle,
            )
        }
    }
}

@Composable
internal fun DestinationBadge(destination: NavigationDestination) {
    when (val badge = destination.resolvedBadge()) {
        ResolvedBadge.None -> Unit
        ResolvedBadge.Dot -> Badge()
        is ResolvedBadge.Label -> StatusBadge(text = badge.text, tone = badge.tone)
    }
}
