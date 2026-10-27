package com.softistx.material.school.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.softistx.material.components.button.CopyButton
import com.softistx.material.components.content.ActionChip
import com.softistx.material.components.form.CopyField
import com.softistx.material.components.media.PersonCard
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.common.say
import com.softistx.material.school.data.Student
import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolSession

/** The guardians, each with a way to reach them. */
@Composable
fun ContactsTab(
    student: Student,
    session: SchoolSession,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
        student.guardians.forEach { guardian ->
            PersonCard(name = guardian.name, supporting = guardian.relation)
            CopyField(value = guardian.email, label = "Email")
            Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                CopyButton(text = guardian.phone, description = "Copy ${guardian.phone}")
                ActionChip(text = "Call ${guardian.phone}", onClick = { session.toaster.say("Calling ${guardian.name}") })
                ActionChip(text = "Message", onClick = { session.open(SchoolScreen.Messages) })
            }
        }
    }
}
