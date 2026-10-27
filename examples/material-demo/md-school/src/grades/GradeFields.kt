package com.softistx.material.school.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.softistx.material.components.datetime.DateField
import com.softistx.material.components.form.Autocomplete
import com.softistx.material.components.form.Checkbox
import com.softistx.material.components.form.HelperText
import com.softistx.material.components.form.QuantityField
import com.softistx.material.components.form.RadioGroup
import com.softistx.material.components.form.SelectField
import com.softistx.material.components.form.TextField
import com.softistx.material.components.form.TextareaField
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.data.GradeKind
import com.softistx.material.school.data.Students
import com.softistx.material.school.data.Subject

/** The nine fields of the grade form, each wired to one property of [GradeInput]. */
@Composable
fun GradeFields(
    form: GradeForm,
    studentQuery: String,
    onStudentQuery: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        Autocomplete(
            value = studentQuery,
            onValueChange = {
                onStudentQuery(it)
                form.update(GradeInput::student) { current -> current.copy(student = null) }
            },
            options = Students.all.filter { it.name.contains(studentQuery, ignoreCase = true) }.take(6),
            onSelect = { student ->
                onStudentQuery(student.name)
                form.update(GradeInput::student) { it.copy(student = student) }
            },
            label = "Student",
            placeholder = "Start typing a name",
            optionLabel = { it.name },
        )
        form.error(GradeInput::student)?.let { HelperText(helper = null, error = it) }
        SelectField(
            value = form.value.subject,
            onValueChange = { v -> form.update(GradeInput::subject) { it.copy(subject = v) } },
            options = Subject.entries,
            label = "Subject",
            optionLabel = { it.label },
            isError = form.hasError(GradeInput::subject),
            supportingText = form.error(GradeInput::subject),
        )
        RadioGroup(
            value = form.value.kind,
            onValueChange = { v -> form.update(GradeInput::kind) { it.copy(kind = v) } },
            options = GradeKind.entries,
            label = "Type",
            optionLabel = { it.label },
        )
        TextField(
            value = form.value.score,
            onValueChange = { v -> form.update(GradeInput::score) { it.copy(score = v) } },
            label = "Mark out of 20",
            keyboardType = KeyboardType.Decimal,
            isError = form.hasError(GradeInput::score),
            supportingText = form.error(GradeInput::score),
        )
        QuantityField(
            value = form.value.weight,
            onValueChange = { v -> form.update(GradeInput::weight) { it.copy(weight = v) } },
            range = 1..5,
            label = "Weight",
        )
        DateField(
            value = form.value.date,
            onValueChange = { v -> form.update(GradeInput::date) { it.copy(date = v) } },
            label = "Date of the assessment",
        )
        TextareaField(
            value = form.value.comment,
            onValueChange = { v -> form.update(GradeInput::comment) { it.copy(comment = v) } },
            label = "Comment",
            maxLength = 200,
            isError = form.hasError(GradeInput::comment),
            supportingText = form.error(GradeInput::comment),
        )
        Checkbox(
            value = form.value.notify,
            onValueChange = { v -> form.update(GradeInput::notify) { it.copy(notify = v) } },
            label = "Tell the guardians",
        )
    }
}
