package com.softistx.material.components.data

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.components.content.Card
import com.softistx.material.foundation.theme.StxTheme

@Immutable
data class TableColumn<T>(
    val header: String,
    val sortable: Boolean = true,
    val value: (T) -> String,
)

/**
 * Which column a [DataTable] is sorted by, and which way. [column] is an index into `columns`, so
 * reordering the columns changes it.
 */
@Immutable
data class TableSort(
    val column: Int,
    val direction: SortDirection = SortDirection.Asc,
)

/**
 * Rows of data. A table when the pane is wide; cards of [Description] when it is not.
 *
 * A table that scrolls sideways on a phone is the failure this avoids. [collapseBelow] is the
 * offered width, not the window, so a narrow pane on a desktop folds the same way.
 *
 * With [onSortChange] every header is a button: a click sorts by that column, ascending first and
 * then flipping with [cycleSortDirection], and [sort] marks the current one with an arrow and
 * announces it; a column with `sortable = false` stays plain text.
 * The table only reports the click: the caller re-sorts `rows`. With [sort] and no [onSortChange]
 * the arrow and its announcement are display-only. The cards have no headers, so a folded table sorts through a [SortControl] beside it.
 * [style] dresses the cards and, with [onRowClick], the rows; [headerStyle] only the sortable headers; a row is
 * clickable with [onRowClick], which stays last so a trailing lambda still reads as the row click.
 */
@Composable
fun <T> DataTable(
    columns: List<TableColumn<T>>,
    rows: List<T>,
    modifier: Modifier = Modifier,
    collapseBelow: Dp = 600.dp,
    sort: TableSort? = null,
    onSortChange: ((TableSort) -> Unit)? = null,
    headerStyle: Style = Style,
    style: Style = Style,
    onRowClick: ((T) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < collapseBelow) {
            Column {
                rows.forEach { row ->
                    Card(
                        onClick = onRowClick?.let { handler -> { handler(row) } },
                        modifier = Modifier.padding(bottom = StxTheme.spacing.sm),
                        style = style,
                    ) {
                        Description(items = columns.map { DescriptionItem(it.header, it.value(row)) })
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = StxTheme.spacing.xs)) {
                    columns.forEachIndexed { index, column ->
                        TableHeader(
                            text = column.header,
                            direction = sort?.takeIf { it.column == index }?.direction,
                            onClick = onSortChange?.takeIf { column.sortable }?.let { change -> { change(nextSort(sort, index)) } },
                            style = headerStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                HorizontalDivider()
                rows.forEach { row ->
                    TableRow(columns, row, style, onRowClick?.let { handler -> { handler(row) } })
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The sort a click on column [index] asks for: that column ascending, or flipped when it is the current one. */
internal fun nextSort(
    current: TableSort?,
    index: Int,
): TableSort =
    if (current != null && current.column == index) {
        TableSort(index, cycleSortDirection(current.direction))
    } else {
        TableSort(index, SortDirection.Asc)
    }
