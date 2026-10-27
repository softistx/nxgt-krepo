package com.softistx.material.school.dashboard

import com.softistx.material.school.data.Announcement
import com.softistx.material.school.data.Announcements
import com.softistx.material.school.data.Grade
import com.softistx.material.school.data.Lesson
import com.softistx.material.school.data.SchoolClock
import com.softistx.material.school.data.Timetable
import com.softistx.material.school.session.SchoolSession

/** Everything the dashboard shows, read once from the session so the loaded face is a plain value. */
data class DashboardData(
    val lessons: List<Lesson>,
    val recent: List<Grade>,
    val announcements: List<Announcement>,
) {
    companion object {
        val Empty = DashboardData(emptyList(), emptyList(), emptyList())

        fun of(session: SchoolSession) =
            DashboardData(
                lessons = Timetable.lessons(Timetable.Mine, SchoolClock.today.dayOfWeek),
                recent = session.grades.sortedByDescending { it.date }.take(5),
                announcements = Announcements.all,
            )
    }
}
