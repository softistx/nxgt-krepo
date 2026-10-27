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
 * One column title. Plain text without [onClick]; with it a button that sorts, showing an arrow and
 * announcing its direction while [direction] is not null.
 */
@Composable
internal fun TableHeader(
    text: String,
    direction: SortDirection?,
    onClick: (() -> Unit)?,
    style: Style,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = true }
    val interactive =
        if (onClick == null) {
            Modifier
        } else {
            Modifier
                .styleable(styleState, sortHeaderStyle, style)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = "Sort by $text",
                    onClick = onClick,
                ).semantics { direction?.let { stateDescription = if (it == SortDirection.Asc) "Ascending" else "Descending" } }
        }
    Row(
        modifier = modifier.then(interactive),
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
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = true }
    val interactive =
        if (onClick == null) {
            Modifier
        } else {
            Modifier
                .styleable(styleState, tableRowStyle, style)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
        }
    Row(modifier = Modifier.fillMaxWidth().then(interactive).padding(vertical = StxTheme.spacing.sm)) {
        columns.forEach { column ->
            Typography(text = column.value(row), modifier = Modifier.weight(1f))
        }
    }
}
