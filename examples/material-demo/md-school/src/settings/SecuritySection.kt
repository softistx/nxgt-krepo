package com.softistx.material.school.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.form.CopyField
import com.softistx.material.components.form.OtpField
import com.softistx.material.components.form.PasswordMeter
import com.softistx.material.components.form.TextField
import com.softistx.material.school.common.say
import com.softistx.material.school.session.SchoolSession

/** A four-digit lock for the grade book and the school code to share with a new colleague. */
@Composable
fun SecuritySection(session: SchoolSession) {
    var pin by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Card {
        SectionHeader(title = "Security")
        OtpField(
            value = pin,
            onValueChange = {
                pin = it
                if (it.length == 4) session.toaster.say("Grade book lock set")
            },
            length = 4,
            label = "Grade book PIN",
            helper = "Asked after five minutes without use",
        )
        TextField(value = password, onValueChange = { password = it }, label = "New password", secret = true)
        PasswordMeter(value = password)
        CopyField(value = "ALVAREZ-7A-2026", label = "School invitation code")
    }
}
