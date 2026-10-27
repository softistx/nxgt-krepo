package com.softistx.material.school.dashboard

import androidx.compose.runtime.Composable
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.media.Avatar
import com.softistx.material.components.surface.HoverCard
import com.softistx.material.school.common.GradeBadge
import com.softistx.material.school.data.Grade
import com.softistx.material.school.data.Students
import com.softistx.material.school.data.short
import com.softistx.material.school.session.SchoolSession

/** The latest marks entered, each opening its student. */
@Composable
fun RecentGradesCard(
    grades: List<Grade>,
    session: SchoolSession,
) {
    Card {
        SectionHeader(title = "Recent grades", supporting = "Latest five entries")
        if (grades.isEmpty()) {
            EmptyState(title = "No grades yet", description = "Marks you enter show up here.")
        }
        grades.forEach { grade ->
            val student = Students.byId(grade.studentId) ?: return@forEach
            ListTile(
                title = student.name,
                supporting = "${grade.subject.label}, ${grade.kind.label}, ${grade.date.short()}",
                onClick = { session.openStudent(student.id) },
                leading = {
                    HoverCard(title = student.name, text = "Class ${student.className}, ${student.email}") {
                        Avatar(name = student.name)
                    }
                },
                trailing = { GradeBadge(grade.score) },
            )
        }
    }
}
