package com.softistx.material.school.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.school.dashboard.DashboardScreen
import com.softistx.material.school.grades.GradesScreen
import com.softistx.material.school.messages.MessagesScreen
import com.softistx.material.school.settings.SettingsScreen
import com.softistx.material.school.students.StudentsScreen
import com.softistx.material.school.timetable.TimetableScreen

/**
 * The six screens, as a list a test or a screenshot run can walk. Each entry is a plain composable
 * of the [SchoolSession]: nothing here needs a click to reach.
 */
enum class SchoolScreen(
    val label: String,
    val icon: ImageVector,
    val content: @Composable (SchoolSession) -> Unit,
) {
    Dashboard("Home", StxIcons.Home, { DashboardScreen(it) }),
    Students("Students", StxIcons.Person, { StudentsScreen(it) }),
    Grades("Grades", StxIcons.Star, { GradesScreen(it) }),
    Timetable("Timetable", StxIcons.Calendar, { TimetableScreen(it) }),
    Messages("Messages", StxIcons.Inbox, { MessagesScreen(it) }),
    Settings("Settings", StxIcons.Menu, { SettingsScreen(it) }),
}
