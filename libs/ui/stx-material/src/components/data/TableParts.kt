package com.softistx.material.components.data

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * A press target: this modifier, dressed by [base] then the caller's [style], and clickable as a
 * button (labelled [label] when given) when [onClick] is not null. Without it the modifier is
 * unchanged, so a plain header or a row with no click carries no style, no press and no role.
 */
@Composable
internal fun Modifier.pressable(
    base: Style,
    style: Style,
    onClick: (() -> Unit)?,
    label: String? = null,
): Modifier {
    if (onClick == null) return this
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = true }
    return styleable(styleState, base, style).clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        role = Role.Button,
        onClickLabel = label,
        onClick = onClick,
    )
}

/**
 * One column title. Plain text without [onClick]; with it a button that sorts. While [direction] is
 * not null it shows an arrow and announces the direction, clickable or not (a display-only sort).
 */
@Composable
internal fun TableHeader(
    text: String,
    direction: SortDirection?,
    onClick: (() -> Unit)?,
    style: Style,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .pressable(sortHeaderStyle, style, onClick, "Sort by $text")
                .semantics(mergeDescendants = true) { direction?.let { stateDescription = if (it == SortDirection.Asc) "Ascending" else "Descending" } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs),
    ) {
        Typography(text = text, variant = TypographyVariant.LabelLarge, emphasis = Emphasis.Medium)
        if (direction != null) {
            Icon(
                icon = if (direction == SortDirection.Asc) StxIcons.ChevronUp else StxIcons.ChevronDown,
                description = null,
                size = IconSize.Small,
            )
        }
    }
}

/** A data row of the wide table, clickable (and pressing to 0.99) when [onClick] is given. */
@Composable
internal fun <T> TableRow(
    columns: List<TableColumn<T>>,
    row: T,
    style: Style,
    onClick: (() -> Unit)?,
) {
    Row(modifier = Modifier.fillMaxWidth().pressable(tableRowStyle, style, onClick).padding(vertical = StxTheme.spacing.sm)) {
        columns.forEach { column ->
            Typography(text = column.value(row), modifier = Modifier.weight(1f))
        }
    }
}
