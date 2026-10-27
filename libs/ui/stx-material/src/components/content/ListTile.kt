package com.softistx.material.components.content

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * One row of a list. Material 3's `ListItem`, which owns the two-line height, the leading and
 * trailing alignment, the content padding, the text colours and, when the tile is interactive, the
 * state layers and a shape that morphs under the press, the hover and the focus.
 *
 * A tile is interactive **iff** [onClick] is not null. Interactive and with [selected] left null, it
 * is announced as a button.
 *
 * - [selected] makes it a single choice, as a list-detail's current row is: `true`/`false` fills the
 *   chosen row with M3's selected tone and announces a radio button that is or is not selected.
 *   It needs [onClick]; without one it is refused.
 * - [group] makes it one tile of a segmented list (see [ListTileGroup]): M3's `SegmentedListItem`,
 *   with a `surfaceContainerLow` container and squarer seams. M3 has no static segmented item, so
 *   it needs [onClick] too; without one it is refused.
 */
@Composable
fun ListTile(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    selected: Boolean? = null,
    group: ListTileGroup? = null,
    style: Style = Style,
    trailing: @Composable (() -> Unit)? = null,
) {
    require(onClick != null || (selected == null && group == null)) {
        "ListTile: selected and group need an onClick; a tile without one is a plain row"
    }
    val interactionSource = remember { MutableInteractionSource() }
    val slots =
        TileSlots(
            headline = { Typography(text = title, variant = TypographyVariant.BodyLarge) },
            supporting =
                supporting?.let {
                    { Typography(text = it, variant = TypographyVariant.BodySmall, emphasis = Emphasis.Medium) }
                },
            leading = leading,
            trailing = trailing,
        )
    val styled = modifier.styledBy(style, enabled = enabled, base = listTileStyle, interactionSource = interactionSource)
    when {
        onClick == null -> PlainRow(slots, styled)
        group != null -> SegmentedRow(slots, styled, onClick, enabled, selected, group, interactionSource)
        else -> ChoiceRow(slots, styled, onClick, enabled, selected, interactionSource)
    }
}

private class TileSlots(
    val headline: @Composable () -> Unit,
    val supporting: (@Composable () -> Unit)?,
    val leading: (@Composable () -> Unit)?,
    val trailing: (@Composable () -> Unit)?,
)

@Composable
private fun PlainRow(
    slots: TileSlots,
    modifier: Modifier,
) = ListItem(
    headlineContent = slots.headline,
    modifier = modifier,
    supportingContent = slots.supporting,
    leadingContent = slots.leading,
    trailingContent = slots.trailing,
    colors = ListItemDefaults.colors(),
)

@Composable
private fun ChoiceRow(
    slots: TileSlots,
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
    selected: Boolean?,
    interactionSource: MutableInteractionSource,
) {
    if (selected == null) {
        ListItem(
            onClick = onClick,
            modifier = modifier.asButton(),
            enabled = enabled,
            leadingContent = slots.leading,
            trailingContent = slots.trailing,
            supportingContent = slots.supporting,
            interactionSource = interactionSource,
            content = slots.headline,
        )
    } else {
        ListItem(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            leadingContent = slots.leading,
            trailingContent = slots.trailing,
            supportingContent = slots.supporting,
            interactionSource = interactionSource,
            content = slots.headline,
        )
    }
}

@Composable
private fun SegmentedRow(
    slots: TileSlots,
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
    selected: Boolean?,
    group: ListTileGroup,
    interactionSource: MutableInteractionSource,
) {
    val shapes = ListItemDefaults.segmentedShapes(group.index, group.count)
    val colors = ListItemDefaults.segmentedColors(containerColor = StxTheme.colors.scheme.surfaceContainerLow)
    if (selected == null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier.asButton(),
            enabled = enabled,
            leadingContent = slots.leading,
            trailingContent = slots.trailing,
            supportingContent = slots.supporting,
            colors = colors,
            interactionSource = interactionSource,
            content = slots.headline,
        )
    } else {
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = slots.leading,
            trailingContent = slots.trailing,
            supportingContent = slots.supporting,
            colors = colors,
            interactionSource = interactionSource,
            content = slots.headline,
        )
    }
}

/** M3's clickable `ListItem` sets no role; this merges the row into one node that is a button. */
private fun Modifier.asButton(): Modifier = semantics(mergeDescendants = true) { role = Role.Button }
