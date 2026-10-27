package com.softistx.material.school.settings

import androidx.compose.runtime.Composable
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.form.SelectField
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.common.say
import com.softistx.material.school.session.Languages
import com.softistx.material.school.session.SchoolSession

/** The language choice. The showcase text stays English; the choice is real state. */
@Composable
fun LanguageSection(session: SchoolSession) {
    val preferences = session.preferences
    Card {
        SectionHeader(title = "Language", supporting = "Interface and messages to guardians")
        SelectField(
            value = preferences.language,
            onValueChange = {
                preferences.language = it
                session.toaster.say("Language set to $it (the showcase stays in English)", Tone.Info)
            },
            options = Languages,
            label = "Language",
        )
    }
}
