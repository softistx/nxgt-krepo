package com.softistx.material.school.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.datetime.Calendar
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.common.ScreenFrame
import com.softistx.material.school.common.StateHost
import com.softistx.material.school.common.isWide
import com.softistx.material.school.session.DataState
import com.softistx.material.school.session.SchoolSession
import com.softistx.material.school.session.of

/** The week's lessons: five columns and a calendar on a wide window, one day at a time on a phone. */
@Composable
fun TimetableScreen(session: SchoolSession) {
    val ui = rememberTimetableUi()
    val state = session.data.of(ui.plan(), emptyMap())
    ScreenFrame(title = "Timetable", session = session) {
        TimetableControls(ui)
        StateHost(state = state, onRetry = { session.data = DataState.Loaded }) { plan ->
            if (plan.isEmpty()) {
                EmptyState(
                    title = "No timetable published",
                    description = "The office has not released this term's timetable yet.",
                    action = { Button(text = "Ask the office", onClick = {}) },
                )
            } else {
                NextLesson(plan)
                BoxWithConstraints {
                    if (maxWidth.isWide()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) { WeekGrid(plan, ui) }
                            Column(Modifier.width(360.dp)) { Calendar(value = ui.anchor, onValueChange = { ui.anchor = it }) }
                        }
                    } else {
                        DayTabs(plan, ui)
                    }
                }
            }
        }
    }
}
