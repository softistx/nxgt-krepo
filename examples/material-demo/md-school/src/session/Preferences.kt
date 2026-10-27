package com.softistx.material.school.session

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.softistx.material.foundation.theme.ColorMode
import com.softistx.material.foundation.theme.DefaultSeed
import kotlinx.datetime.LocalTime

val Languages: List<String> = listOf("English", "Français", "Español", "Deutsch")

enum class Channel(
    val label: String,
) {
    Email("Email"),
    Push("Push"),
    Sms("SMS"),
}

/** The seeds the settings screen offers, each a name the reader recognises. */
val SchoolSeeds: List<Pair<String, Color>> =
    listOf(
        "Indigo" to DefaultSeed,
        "Teal" to Color(0xFF00796B),
        "Rose" to Color(0xFFC2185B),
        "Amber" to Color(0xFFEF6C00),
        "Forest" to Color(0xFF2E7D32),
    )

/** What the settings screen edits. Held once, read by the theme and by every screen. */
@Stable
class Preferences(
    mode: ColorMode,
    val animated: Boolean = true,
) {
    var colorMode by mutableStateOf(mode)
    var seed by mutableStateOf(DefaultSeed)
    var dynamicColor by mutableStateOf(false)
    var language by mutableStateOf(Languages.first())
    var gradeAlerts by mutableStateOf(true)
    var messageAlerts by mutableStateOf(true)
    var weeklyDigest by mutableStateOf(false)
    var channels by mutableStateOf(setOf(Channel.Email, Channel.Push))
    var reminderMinutes by mutableStateOf(15f)
    var quietFrom by mutableStateOf<LocalTime?>(LocalTime(21, 0))
}
