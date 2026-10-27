package com.softistx.material.school.timetable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.school.data.Lesson
import com.softistx.material.school.data.SchoolClock
import com.softistx.material.school.data.Timetable
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** A week's lessons by day. */
typealias WeekPlan = Map<DayOfWeek, List<Lesson>>

/** What the timetable screen is looking at: whose lessons, which week, which day on a phone. */
@Stable
class TimetableUi {
    var filter by mutableStateOf(Timetable.MINE)
    var anchor by mutableStateOf<LocalDate?>(SchoolClock.today)
    var day by mutableStateOf(
        SchoolClock.today.dayOfWeek.ordinal
            .coerceAtMost(4),
    )
    var showRooms by mutableStateOf(true)

    val monday: LocalDate get() = SchoolClock.weekStart(anchor ?: SchoolClock.today)

    fun date(index: Int): LocalDate = monday.plus(index, DateTimeUnit.DAY)

    fun plan(): WeekPlan = SchoolClock.teachingDays.associateWith { Timetable.lessons(filter, it) }
}

@Composable
fun rememberTimetableUi(): TimetableUi = remember { TimetableUi() }
