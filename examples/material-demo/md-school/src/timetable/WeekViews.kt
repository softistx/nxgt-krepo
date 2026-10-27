package com.softistx.material.school.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.feedback.StatusBadge
import com.softistx.material.components.navigation.Tabs
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.data.SchoolClock
import com.softistx.material.school.data.label
import com.softistx.material.school.data.short

/** Wide: the five days side by side, each a column of lesson cards. */
@Composable
fun WeekGrid(
    plan: WeekPlan,
    ui: TimetableUi,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        SchoolClock.teachingDays.forEachIndexed { index, day ->
            val date = ui.date(index)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                Typography(text = "${day.label.take(3)} ${date.short()}", variant = TypographyVariant.TitleSmall)
                if (date == SchoolClock.today) StatusBadge(text = "Today", tone = Tone.Info)
                plan[day].orEmpty().forEach { LessonCard(it, date, ui.showRooms) }
            }
        }
    }
}

/** Compact: a tab per day, and that day's lessons under it. */
@Composable
fun DayTabs(
    plan: WeekPlan,
    ui: TimetableUi,
) {
    val days = SchoolClock.teachingDays
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        Tabs(labels = days.map { it.label.take(3) }, selected = ui.day, onSelect = { ui.day = it })
        val date = ui.date(ui.day)
        val lessons = plan[days[ui.day]].orEmpty()
        Typography(text = date.short(), variant = TypographyVariant.TitleMedium)
        if (lessons.isEmpty()) {
            EmptyState(title = "No lessons", description = "Nothing is scheduled on this day.")
        }
        lessons.forEach { LessonCard(it, date, ui.showRooms) }
    }
}
