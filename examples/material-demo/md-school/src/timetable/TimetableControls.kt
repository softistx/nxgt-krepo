package com.softistx.material.school.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.ToggleButton
import com.softistx.material.components.datetime.DateField
import com.softistx.material.components.datetime.RelativeTime
import com.softistx.material.components.form.SelectField
import com.softistx.material.components.surface.HelpTip
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.data.SchoolClock
import com.softistx.material.school.data.Timetable
import com.softistx.material.school.data.hhmm
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/** Whose timetable, which week, and whether rooms are shown. */
@Composable
fun TimetableControls(ui: TimetableUi) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectField(
            value = ui.filter,
            onValueChange = { ui.filter = it },
            options = Timetable.options,
            label = "Timetable",
            modifier = Modifier.weight(1f),
        )
        DateField(
            value = ui.anchor,
            onValueChange = { ui.anchor = it },
            label = "Week of",
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        ToggleButton(
            text = "Rooms",
            checked = ui.showRooms,
            onCheckedChange = { ui.showRooms = it },
            icon = StxIcons.Pin,
        )
        HelpTip(text = "Wednesday afternoons are free for every class.")
    }
}

/** The lesson that starts next today, with how long until it does. */
@Composable
fun NextLesson(plan: WeekPlan) {
    val next = plan[SchoolClock.today.dayOfWeek].orEmpty().firstOrNull { it.start > SchoolClock.time } ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Typography(text = "Next: ${next.subject.label} with ${next.className} at ${next.start.hhmm()},")
        RelativeTime(at = SchoolClock.today.atTime(next.start).toInstant(TimeZone.UTC), now = SchoolClock.now)
    }
}
