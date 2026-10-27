package com.softistx.material.school.grades

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonRow
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.button.ConfirmButton
import com.softistx.material.components.button.SplitButton
import com.softistx.material.components.content.Card
import com.softistx.material.components.form.FormSection
import com.softistx.material.components.form.state.rememberForm
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.school.common.say
import com.softistx.material.school.data.Students
import com.softistx.material.school.session.SchoolSession

/** The form to enter a mark, or to correct the one picked in the table. */
@Composable
fun GradeFormCard(
    session: SchoolSession,
    query: GradesQuery,
    modifier: Modifier = Modifier,
) {
    val form = rememberForm { GradeForm() }
    var studentQuery by remember { mutableStateOf("") }
    val editing = query.editing
    LaunchedEffect(editing) {
        if (editing != null) {
            form.update(GradeInput::student) { GradeInput.from(editing) }
            studentQuery = Students.byId(editing.studentId)?.name.orEmpty()
        }
    }
    fun finish(addAnother: Boolean) {
        query.editing = null
        form.reset()
        studentQuery = ""
        if (!addAnother) query.tab = 0
    }
    fun save(addAnother: Boolean) =
        form.submit { input ->
            val grade = input.toGrade(id = session.grades.maxOf { it.id } + 1)
            val at = session.grades.indexOfFirst { it.id == grade.id }
            if (at >= 0) session.grades[at] = grade else session.grades.add(grade)
            session.toaster.say(if (at >= 0) "Grade updated" else "Grade saved for ${input.student?.name}")
            finish(addAnother)
        }

    Card(modifier = modifier) {
        FormSection(
            title = if (editing != null) "Correct a grade" else "Enter a grade",
            supporting = "Marks are out of 20; half points are fine.",
        ) {
            GradeFields(form, studentQuery) { studentQuery = it }
            ButtonRow(align = Alignment.End) {
                if (editing != null) {
                    ConfirmButton(
                        text = "Delete",
                        onConfirm = {
                            session.grades.removeAll { it.id == editing.id }
                            session.toaster.say("Grade deleted")
                            finish(false)
                        },
                    )
                }
                Button(text = "Reset", onClick = { finish(true) }, variant = ButtonVariant.Ghost)
                SplitButton(
                    text = if (editing != null) "Update" else "Save",
                    onClick = { save(false) },
                    overflow = listOf(MenuItem("Save and add another", { save(true) })),
                )
            }
        }
    }
}
