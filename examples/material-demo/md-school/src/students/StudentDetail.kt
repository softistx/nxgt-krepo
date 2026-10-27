package com.softistx.material.school.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.IconToggle
import com.softistx.material.components.button.OverflowAction
import com.softistx.material.components.button.OverflowBar
import com.softistx.material.components.data.EntityHeader
import com.softistx.material.components.media.Avatar
import com.softistx.material.components.navigation.Breadcrumb
import com.softistx.material.components.navigation.BreadcrumbItem
import com.softistx.material.components.navigation.Tabs
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.common.say
import com.softistx.material.school.data.Student
import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolSession

private val Sections = listOf("Profile", "Grades", "Attendance", "Contacts")

/** One student: a header with actions, then profile, grades, attendance and contacts in tabs. */
@Composable
fun StudentDetail(
    student: Student,
    session: SchoolSession,
    modifier: Modifier = Modifier,
) {
    var tab by remember(student.id) { mutableStateOf(0) }
    var starred by remember(student.id) { mutableStateOf(false) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(StxTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
    ) {
        Breadcrumb(
            items = listOf(BreadcrumbItem("Students") { session.studentId = null }, BreadcrumbItem(student.name)),
        )
        EntityHeader(
            title = student.name,
            supporting = "Class ${student.className}",
            leading = { Avatar(name = student.name, size = 56.dp) },
            actions = {
                IconToggle(
                    icon = StxIcons.Star,
                    description = "Favourite",
                    checked = starred,
                    onCheckedChange = { starred = it },
                )
                OverflowBar(
                    actions =
                        listOf(
                            OverflowAction("Message", StxIcons.Send, { session.open(SchoolScreen.Messages) }),
                            OverflowAction("Enter a grade", StxIcons.Edit, { session.open(SchoolScreen.Grades) }),
                            OverflowAction("Print report", StxIcons.Copy, { session.toaster.say("Report sent to the printer") }),
                        ),
                    maxVisible = 1,
                )
            },
        )
        Tabs(labels = Sections, selected = tab, onSelect = { tab = it }, scrollable = true)
        when (tab) {
            0 -> ProfileTab(student, session)
            1 -> GradesTab(student, session)
            2 -> AttendanceTab(student)
            else -> ContactsTab(student, session)
        }
    }
}
