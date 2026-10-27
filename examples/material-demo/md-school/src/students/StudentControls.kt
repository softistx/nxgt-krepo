package com.softistx.material.school.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.ViewToggle
import com.softistx.material.components.content.FilterBar
import com.softistx.material.components.data.SortControl
import com.softistx.material.components.data.cycleSortDirection
import com.softistx.material.components.navigation.Search
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.data.Students

/** Search, class filter, sort and view mode: everything that narrows or reorders the list. */
@Composable
fun StudentControls(query: StudentsQuery) {
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        Search(
            query = query.text,
            onQueryChange = {
                query.text = it
                query.page = 0
            },
            placeholder = "Search students",
            modifier = Modifier.fillMaxWidth(),
        )
        FilterBar(
            options = Students.classNames,
            selected = query.classes,
            onChange = {
                query.classes = it
                query.page = 0
            },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortControl(
                options = StudentSorts,
                selected = query.sort,
                direction = query.direction,
                onSelectedChange = { query.sort = it },
                onDirectionChange = { query.direction = cycleSortDirection(query.direction) },
                modifier = Modifier.weight(1f),
            )
            ViewToggle(value = query.view, onChange = { query.view = it })
        }
    }
}
