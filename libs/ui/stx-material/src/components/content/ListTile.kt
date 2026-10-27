package com.softistx.material.components.content

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * One row of a list. Material 3's `ListItem`, which owns the two-line height, the leading and
 * trailing alignment, the content padding, the text colours and, when the tile is interactive, the
 * ripple and a shape that morphs under the press, the hover and the focus.
 *
 * A tile is interactive **iff** [onClick] is not null. Interactive, it is announced as a button.
 *
 * - [selected] makes it a single choice, as a list-detail's current row is: `null` (the default) is
 *   a plain button; `true`/`false` fills the chosen row with M3's selected tone and announces it as
 *   a radio button that is or is not selected. It needs [onClick].
 * - [group] makes it one tile of a segmented list (see [ListTileGroup]): M3's `SegmentedListItem`,
 *   with a `surfaceContainerLow` container and squarer seams. It needs [onClick].
 */
@Composable
fun ListTile(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: Style = Style,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    selected: Boolean? = null,
    group: ListTileGroup? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    val styled = modifier.styleable(styleState, listTileStyle, style)
    val headline: @Composable () -> Unit = { Typography(text = title, variant = TypographyVariant.BodyLarge) }
    val supportingContent: (@Composable () -> Unit)? =
        supporting?.let {
            { Typography(text = it, variant = TypographyVariant.BodySmall, emphasis = Emphasis.Medium) }
        }
    val segmented = ListItemDefaults.segmentedColors(containerColor = StxTheme.colors.scheme.surfaceContainerLow)
    when {
        onClick == null ->
            ListItem(
                headlineContent = headline,
                modifier = styled,
                supportingContent = supportingContent,
                leadingContent = leading,
                trailingContent = trailing,
                colors = ListItemDefaults.colors(),
            )

        group != null && selected != null ->
            SegmentedListItem(
                selected = selected,
                onClick = onClick,
                shapes = ListItemDefaults.segmentedShapes(group.index, group.count),
                modifier = styled,
                enabled = enabled,
                leadingContent = leading,
                trailingContent = trailing,
                supportingContent = supportingContent,
                colors = segmented,
                interactionSource = interactionSource,
                content = headline,
            )

        group != null ->
            SegmentedListItem(
                onClick = onClick,
                colors = segmented,
                shapes = ListItemDefaults.segmentedShapes(group.index, group.count),
                modifier = styled.asButton(),
                enabled = enabled,
                leadingContent = leading,
                trailingContent = trailing,
                supportingContent = supportingContent,
                interactionSource = interactionSource,
                content = headline,
            )

        selected != null ->
            ListItem(
                selected = selected,
                onClick = onClick,
                modifier = styled,
                enabled = enabled,
                leadingContent = leading,
                trailingContent = trailing,
                supportingContent = supportingContent,
                interactionSource = interactionSource,
                content = headline,
            )

        else ->
            ListItem(
                onClick = onClick,
                modifier = styled.asButton(),
                enabled = enabled,
                leadingContent = leading,
                trailingContent = trailing,
                supportingContent = supportingContent,
                interactionSource = interactionSource,
                content = headline,
            )
    }
}

/** M3's clickable `ListItem` sets no role; this merges the row into one node that is a button. */
private fun Modifier.asButton(): Modifier = semantics(mergeDescendants = true) { role = Role.Button }
