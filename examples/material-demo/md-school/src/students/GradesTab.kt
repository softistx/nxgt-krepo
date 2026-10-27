package com.softistx.material.school.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.softistx.material.components.content.QuoteBlock
import com.softistx.material.components.data.DataTable
import com.softistx.material.components.data.TableColumn
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.data.Grades
import com.softistx.material.school.data.Student
import com.softistx.material.school.data.Subject
import com.softistx.material.school.data.oneDecimal
import com.softistx.material.school.session.SchoolSession

private class SubjectLine(
    val subject: Subject,
    val marks: String,
    val average: String,
)

/** The student's marks by subject and the weighted average of each. */
@Composable
fun GradesTab(
    student: Student,
    session: SchoolSession,
) {
    val mine = session.grades.filter { it.studentId == student.id }
    val lines =
        Subject.entries.map { subject ->
            val marks = mine.filter { it.subject == subject }
            SubjectLine(subject, marks.joinToString(", ") { it.score.oneDecimal() }, Grades.average(marks).oneDecimal())
        }
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        Typography(text = "Overall ${Grades.average(mine).oneDecimal()}/20", variant = TypographyVariant.TitleMedium)
        mine.maxByOrNull { it.date }?.takeIf { it.comment.isNotBlank() }?.let {
            QuoteBlock(text = it.comment, attribution = it.subject.teacher)
        }
        DataTable(
            columns =
                listOf(
                    TableColumn("Subject") { it.subject.label },
                    TableColumn("Marks") { it.marks },
                    TableColumn("Average") { it.average },
                ),
            rows = lines,
        )
    }
}
