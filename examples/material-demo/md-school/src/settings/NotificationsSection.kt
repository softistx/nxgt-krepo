package com.softistx.material.school.settings

import androidx.compose.runtime.Composable
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.datetime.TimeField
import com.softistx.material.components.form.CheckState
import com.softistx.material.components.form.CheckboxGroup
import com.softistx.material.components.form.SliderField
import com.softistx.material.components.form.Switch
import com.softistx.material.components.form.TriStateCheckbox
import com.softistx.material.school.session.Channel
import com.softistx.material.school.session.Preferences

private fun Set<Channel>.state(): CheckState =
    when (size) {
        0 -> CheckState.Off
        Channel.entries.size -> CheckState.On
        else -> CheckState.Indeterminate
    }

/** What to be told about, through which channels, how early and when to be left alone. */
@Composable
fun NotificationsSection(preferences: Preferences) {
    Card {
        SectionHeader(title = "Notifications")
        Switch(
            preferences.gradeAlerts,
            { preferences.gradeAlerts = it },
            "Grade alerts",
            description = "When a mark is entered for your class",
        )
        Switch(preferences.messageAlerts, { preferences.messageAlerts = it }, "Message alerts", description = "New messages from guardians")
        Switch(preferences.weeklyDigest, { preferences.weeklyDigest = it }, "Weekly digest", description = "Every Friday afternoon")
        TriStateCheckbox(
            state = preferences.channels.state(),
            onClick = {
                preferences.channels = if (preferences.channels.size == Channel.entries.size) emptySet() else Channel.entries.toSet()
            },
            label = "All channels",
        )
        CheckboxGroup(
            value = preferences.channels,
            onValueChange = { preferences.channels = it },
            options = Channel.entries,
            optionLabel = { it.label },
        )
        SliderField(
            value = preferences.reminderMinutes,
            onValueChange = { preferences.reminderMinutes = it },
            label = "Remind me before a lesson",
            range = 5f..60f,
            steps = 10,
            format = { "${it.toInt()} min" },
        )
        TimeField(value = preferences.quietFrom, onValueChange = { preferences.quietFrom = it }, label = "Quiet from")
    }
}
