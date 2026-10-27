package com.softistx.material.school.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonSize
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.Stat
import com.softistx.material.components.data.DataTable
import com.softistx.material.components.data.TableColumn
import com.softistx.material.components.data.TableSort
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.data.Grade
import com.softistx.material.school.data.Grades
import com.softistx.material.school.data.Students
import com.softistx.material.school.data.oneDecimal
import com.softistx.material.school.data.short

private val Columns =
    listOf<TableColumn<Grade>>(
        TableColumn("Student") { Students.byId(it.studentId)?.name.orEmpty() },
        TableColumn("Subject", sortable = false) { it.subject.label },
        TableColumn("Type", sortable = false) { it.kind.label },
        TableColumn("Date") { it.date.short() },
        TableColumn("Score") { it.score.oneDecimal() },
        TableColumn("Weight", sortable = false) { "x${it.weight}" },
    )

/** The sorts the headers offer: the table column each [GradeSorts] entry sorts. */
private val SortColumns = mapOf("Student" to 0, "Date" to 3, "Score" to 4)

/** The filtered marks as a table; a click on a row loads it into the form to be corrected. */
@Composable
fun GradesTable(
    rows: List<Grade>,
    query: GradesQuery,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        if (rows.isEmpty()) {
            EmptyState(
                title = "No marks match",
                description = "Widen the class, subject or period.",
                action = {
                    Button(
                        text = "Show everything",
                        onClick = {
                            query.classIndex = 0
                            query.subjectIndex = 0
                            query.from = null
                            query.to = null
                        },
                        size = ButtonSize.Medium,
                    )
                },
            )
        } else {
            val average = Grades.average(rows)
            Stat(
                value = average.oneDecimal(),
                label = "Average of ${rows.size} marks",
            )
            DataTable(
                columns = Columns,
                rows = rows,
                sort = query.sort?.let { SortColumns[it] }?.let { TableSort(it, query.direction) },
                onSortChange = { next ->
                    SortColumns.entries.firstOrNull { it.value == next.column }?.let { query.sort = it.key }
                    query.direction = next.direction
                },
                onRowClick = {
                    query.editing = it
                    query.tab = 1
                },
            )
        }
    }
}
