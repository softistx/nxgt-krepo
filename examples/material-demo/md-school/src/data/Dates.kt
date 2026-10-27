package com.softistx.material.school.data

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

private val months =
    listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

val DayOfWeek.label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

/** "Monday 26 October" */
fun LocalDate.pretty(): String = "${dayOfWeek.label} $day ${months[month.ordinal]}"

/** "26 Oct" */
fun LocalDate.short(): String = "$day ${months[month.ordinal].take(3)}"
