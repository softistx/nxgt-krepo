package com.softistx.material.school.students

import androidx.compose.runtime.Composable
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.media.Avatar
import com.softistx.material.components.media.PersonCard
import com.softistx.material.components.surface.ContextMenu
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.common.GradeBadge
import com.softistx.material.school.common.say
import com.softistx.material.school.data.Grades
import com.softistx.material.school.data.Student
import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolSession

private fun SchoolSession.averageOf(student: Student): Double = Grades.average(grades.filter { it.studentId == student.id })

/** One row; a right click or a long press offers the same actions as the detail view. */
@Composable
fun StudentRow(
    student: Student,
    session: SchoolSession,
) {
    ContextMenu(items = studentMenu(student, session)) {
        ListTile(
            title = student.name,
            supporting = "Class ${student.className}",
            onClick = { session.studentId = student.id },
            leading = { Avatar(name = student.name) },
            trailing = { GradeBadge(session.averageOf(student)) },
        )
    }
}

/** One tile of the grid view. */
@Composable
fun StudentCard(
    student: Student,
    session: SchoolSession,
) {
    PersonCard(
        name = student.name,
        supporting = "Class ${student.className}",
        onClick = { session.studentId = student.id },
        action = { GradeBadge(session.averageOf(student)) },
    )
}

private fun studentMenu(
    student: Student,
    session: SchoolSession,
): List<MenuItem> =
    listOf(
        MenuItem("Open", { session.studentId = student.id }, StxIcons.Person),
        MenuItem("Message guardians", { session.open(SchoolScreen.Messages) }, StxIcons.Send),
        MenuItem("Enter a grade", { session.open(SchoolScreen.Grades) }, StxIcons.Edit),
        MenuItem("Show email", { session.toaster.say(student.email, Tone.Info) }, StxIcons.Info),
    )
