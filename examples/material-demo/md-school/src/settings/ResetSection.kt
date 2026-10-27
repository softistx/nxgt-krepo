package com.softistx.material.school.settings

import androidx.compose.runtime.Composable
import com.softistx.material.components.button.ConfirmButton
import com.softistx.material.components.form.DangerZone
import com.softistx.material.school.common.say
import com.softistx.material.school.data.Grades
import com.softistx.material.school.session.SchoolSession

/** Puts the demo data back as it was: the marks entered in this session are dropped. */
@Composable
fun ResetSection(session: SchoolSession) {
    DangerZone(text = "Resetting drops every mark you entered in this session.") {
        ConfirmButton(
            text = "Reset demo data",
            onConfirm = {
                session.grades.clear()
                session.grades.addAll(Grades.all)
                session.archived.clear()
                session.toaster.say("Demo data reset")
            },
        )
    }
}
