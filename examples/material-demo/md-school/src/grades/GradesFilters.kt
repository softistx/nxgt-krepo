package com.softistx.material.school.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.data.SortControl
import com.softistx.material.components.data.cycleSortDirection
import com.softistx.material.components.datetime.DateRangeField
import com.softistx.material.components.navigation.SegmentedControl
import com.softistx.material.components.navigation.Tabs
import com.softistx.material.foundation.theme.StxTheme

/** Class, subject, period and sort: the four ways the results table is narrowed. */
@Composable
fun GradesFilters(query: GradesQuery) {
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        SegmentedControl(
            options = ClassFilters,
            selected = query.classIndex,
            onSelect = { query.classIndex = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Tabs(labels = SubjectTabs, selected = query.subjectIndex, onSelect = { query.subjectIndex = it }, scrollable = true)
        DateRangeField(
            start = query.from,
            end = query.to,
            onValueChange = { s, e ->
                query.from = s
                query.to = e
            },
            label = "Period",
            modifier = Modifier.fillMaxWidth(),
        )
        SortControl(
            options = GradeSorts,
            selected = query.sort,
            direction = query.direction,
            onSelectedChange = { query.sort = it },
            onDirectionChange = { query.direction = cycleSortDirection(query.direction) },
        )
    }
}
