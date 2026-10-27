package com.softistx.material.school.grades

import com.softistx.material.components.form.state.FormState
import com.softistx.material.components.form.state.chosen
import com.softistx.material.components.form.state.maxLength
import com.softistx.material.components.form.state.pattern
import com.softistx.material.components.form.state.required
import com.softistx.material.school.data.Grade
import com.softistx.material.school.data.GradeKind
import com.softistx.material.school.data.SchoolClock
import com.softistx.material.school.data.Student
import com.softistx.material.school.data.Students
import com.softistx.material.school.data.Subject
import io.konform.validation.Validation
import kotlinx.datetime.LocalDate

/** The grade being entered or corrected. `editing` is the id of the grade it replaces, if any. */
data class GradeInput(
    val student: Student? = null,
    val subject: Subject? = null,
    val kind: GradeKind? = GradeKind.Quiz,
    val score: String = "",
    val weight: Int = 1,
    val date: LocalDate? = SchoolClock.today,
    val comment: String = "",
    val notify: Boolean = true,
    val editing: Int? = null,
) {
    fun toGrade(id: Int): Grade =
        Grade(
            id = editing ?: id,
            studentId = student!!.id,
            subject = subject!!,
            score = score.toDouble(),
            weight = weight,
            kind = kind!!,
            date = date ?: SchoolClock.today,
            comment = comment,
        )

    companion object {
        fun from(grade: Grade) =
            GradeInput(
                student = Students.byId(grade.studentId),
                subject = grade.subject,
                kind = grade.kind,
                score = grade.score.toString(),
                weight = grade.weight,
                date = grade.date,
                comment = grade.comment,
                notify = false,
                editing = grade.id,
            )
    }
}

private val ScoreFormat = Regex("(20(\\.0)?|1?\\d(\\.\\d)?)")

class GradeForm :
    FormState<GradeInput>(
        GradeInput(),
        listOf(GradeInput::student, GradeInput::subject, GradeInput::kind, GradeInput::score, GradeInput::comment),
    ) {
    override val validate: Validation<GradeInput> =
        Validation {
            GradeInput::student { chosen("Pick a student from the list") }
            GradeInput::subject { chosen() }
            GradeInput::kind { chosen() }
            GradeInput::score {
                required()
                pattern(ScoreFormat, "Enter a mark from 0 to 20, with at most one decimal")
            }
            GradeInput::comment { maxLength(200) }
        }
}
