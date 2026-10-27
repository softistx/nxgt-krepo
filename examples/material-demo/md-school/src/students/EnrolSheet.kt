package com.softistx.material.school.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.components.form.SelectField
import com.softistx.material.components.form.TextField
import com.softistx.material.components.form.UploadField
import com.softistx.material.components.form.state.rememberForm
import com.softistx.material.components.navigation.Step
import com.softistx.material.components.navigation.StepFooter
import com.softistx.material.components.navigation.Stepper
import com.softistx.material.components.surface.Sheet
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.common.say
import com.softistx.material.school.data.Students
import com.softistx.material.school.session.SchoolSession

private val Steps = listOf(Step("Student"), Step("Class"), Step("Guardian"))

/** Three steps in a bottom sheet: who, which class, who to call. Nothing is stored. */
@Composable
fun EnrolSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    session: SchoolSession,
) {
    Sheet(visible = visible, onDismiss = onDismiss) {
        val form = rememberForm { EnrolForm() }
        var step by remember { mutableStateOf(0) }
        Column(
            modifier = Modifier.padding(StxTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
        ) {
            Typography(text = "Enrol a student", variant = TypographyVariant.TitleLarge)
            Stepper(steps = Steps, current = step, onStep = { step = it })
            EnrolStep(step, form, session)
            StepFooter(
                onNext = {
                    if (step < Steps.lastIndex) {
                        step++
                    } else {
                        form.submit {
                            session.toaster.say("${it.firstName} ${it.lastName} enrolled in ${it.className} (demo only)")
                            onDismiss()
                        }
                    }
                },
                onBack = if (step > 0) ({ step-- }) else null,
                nextLabel = if (step == Steps.lastIndex) "Enrol" else "Continue",
                nextEnabled = form.stepValid(step),
            )
        }
    }
}

@Composable
private fun EnrolStep(
    step: Int,
    form: EnrolForm,
    session: SchoolSession,
) {
    when (step) {
        0 -> {
            TextField(
                value = form.value.firstName,
                onValueChange = { v -> form.update(Enrolment::firstName) { it.copy(firstName = v) } },
                label = "First name",
                isError = form.hasError(Enrolment::firstName),
                supportingText = form.error(Enrolment::firstName),
            )
            TextField(
                value = form.value.lastName,
                onValueChange = { v -> form.update(Enrolment::lastName) { it.copy(lastName = v) } },
                label = "Last name",
                isError = form.hasError(Enrolment::lastName),
                supportingText = form.error(Enrolment::lastName),
            )
            UploadField(onClick = { session.toaster.say("Photo picker is the host's job") }, label = "Add a photo")
        }

        1 ->
            SelectField(
                value = form.value.className,
                onValueChange = { v -> form.update(Enrolment::className) { it.copy(className = v) } },
                options = Students.classNames,
                label = "Class",
                isError = form.hasError(Enrolment::className),
                supportingText = form.error(Enrolment::className),
            )

        else -> {
            TextField(
                value = form.value.guardianName,
                onValueChange = { v -> form.update(Enrolment::guardianName) { it.copy(guardianName = v) } },
                label = "Guardian name",
                isError = form.hasError(Enrolment::guardianName),
                supportingText = form.error(Enrolment::guardianName),
            )
            TextField(
                value = form.value.guardianEmail,
                onValueChange = { v -> form.update(Enrolment::guardianEmail) { it.copy(guardianEmail = v) } },
                label = "Guardian email",
                isError = form.hasError(Enrolment::guardianEmail),
                supportingText = form.error(Enrolment::guardianEmail),
            )
        }
    }
}
