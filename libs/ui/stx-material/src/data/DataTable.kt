package com.softistx.material.data

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.display.Card
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

@Immutable
data class TableColumn<T>(
    val header: String,
    val value: (T) -> String,
)

/**
 * Rows of data. A table when the pane is wide; cards of [Description] when it is not.
 *
 * A table that scrolls sideways on a phone is the failure this avoids. [collapseBelow] is the
 * offered width, not the window, so a narrow pane on a desktop folds the same way.
 */
@Composable
fun <T> DataTable(
    columns: List<TableColumn<T>>,
    rows: List<T>,
    modifier: Modifier = Modifier,
    collapseBelow: Dp = 600.dp,
    onRowClick: ((T) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < collapseBelow) {
            Column {
                rows.forEach { row ->
                    Card(
                        onClick = onRowClick?.let { handler -> { handler(row) } },
                        modifier = Modifier.padding(bottom = StxTheme.spacing.sm),
                    ) {
                        Description(items = columns.map { DescriptionItem(it.header, it.value(row)) })
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = StxTheme.spacing.xs)) {
                    columns.forEach { column ->
                        Typography(
                            text = column.header,
                            variant = TypographyVariant.LabelLarge,
                            emphasis = Emphasis.Medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                HorizontalDivider()
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = StxTheme.spacing.sm),
                    ) {
                        columns.forEach { column ->
                            Typography(
                                text = column.value(row),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
