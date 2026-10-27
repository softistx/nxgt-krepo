package com.softistx.material.school.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ResponsiveButton
import com.softistx.material.components.button.ViewMode
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.data.Pagination
import com.softistx.material.components.layout.ResponsiveGrid
import com.softistx.material.components.layout.ScrollToTop
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.data.Student
import com.softistx.material.school.session.SchoolSession

/** The list pane: controls on top, the page of students below, in a list or a grid. */
@Composable
fun StudentList(
    students: List<Student>,
    query: StudentsQuery,
    session: SchoolSession,
    modifier: Modifier = Modifier,
) {
    var enrolling by remember { mutableStateOf(false) }
    val shown = query.apply(students, session.grades)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        StudentControls(query)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Typography(text = "${shown.size} students", modifier = Modifier.weight(1f))
            ResponsiveButton(text = "New student", icon = StxIcons.Add, onClick = { enrolling = true })
        }
        when {
            students.isEmpty() ->
                EmptyState(
                    title = "No students enrolled",
                    description = "Enrol the first student to start tracking marks.",
                    action = { Button(text = "Enrol a student", onClick = { enrolling = true }) },
                )
            shown.isEmpty() ->
                EmptyState(
                    illustration = { Icon(StxIcons.Search, description = null, size = IconSize.XLarge) },
                    title = "No student matches",
                    description = "Try another name or clear the class filter.",
                    action = { Button(text = "Clear filters", onClick = query::clear) },
                )
            query.view == ViewMode.Grid -> StudentGrid(shown, session)
            else -> StudentRows(shown, query, session)
        }
    }
    EnrolSheet(visible = enrolling, onDismiss = { enrolling = false }, session = session)
}

@Composable
private fun StudentGrid(
    shown: List<Student>,
    session: SchoolSession,
) {
    ResponsiveGrid(items = shown, modifier = Modifier.fillMaxSize(), minSize = 220.dp) {
        StudentCard(it, session)
    }
}

@Composable
private fun StudentRows(
    shown: List<Student>,
    query: StudentsQuery,
    session: SchoolSession,
) {
    val lastPage = (shown.size - 1) / PageSize
    val page = query.page.coerceIn(0, lastPage)
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(shown.drop(page * PageSize).take(PageSize), key = { it.id }) { StudentRow(it, session) }
            item {
                Pagination(
                    hasPrevious = page > 0,
                    hasNext = page < lastPage,
                    onPrevious = { query.page = page - 1 },
                    onNext = { query.page = page + 1 },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Page ${page + 1} of ${lastPage + 1}",
                )
            }
        }
        ScrollToTop(listState)
    }
}
