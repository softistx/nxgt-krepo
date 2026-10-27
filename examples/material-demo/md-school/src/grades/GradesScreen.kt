package com.softistx.material.school.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.navigation.SegmentedControl
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.common.ScreenFrame
import com.softistx.material.school.common.StateHost
import com.softistx.material.school.common.isWide
import com.softistx.material.school.session.DataState
import com.softistx.material.school.session.SchoolSession
import com.softistx.material.school.session.of

/** A sortable table of marks and the form to enter or correct one: side by side, or in two tabs. */
@Composable
fun GradesScreen(session: SchoolSession) {
    val query = rememberGradesQuery()
    val rows by remember { derivedStateOf { query.apply(session.grades) } }
    val state = session.data.of(rows, emptyList())
    ScreenFrame(title = "Grades", session = session) {
        BoxWithConstraints {
            val gap = Arrangement.spacedBy(StxTheme.spacing.md)
            val results: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = gap) {
                    GradesFilters(query)
                    StateHost(state = state, onRetry = { session.data = DataState.Loaded }) { rows ->
                        GradesTable(rows, query)
                    }
                }
            }
            if (maxWidth.isWide()) {
                Row(horizontalArrangement = gap, verticalAlignment = Alignment.Top) {
                    results(Modifier.weight(1.3f))
                    GradeFormCard(session, query, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = gap) {
                    SegmentedControl(
                        options = listOf("Results", "Enter a grade"),
                        selected = query.tab,
                        onSelect = { query.tab = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.tab == 0) results(Modifier) else GradeFormCard(session, query)
                }
            }
        }
    }
}
