package com.softistx.material.school.data

import kotlinx.datetime.LocalDate

data class Guardian(
    val name: String,
    val relation: String,
    val phone: String,
    val email: String,
)

enum class AbsenceKind(
    val label: String,
) {
    Excused("Excused"),
    Unexcused("Unexcused"),
    Late("Late"),
}

data class Absence(
    val date: LocalDate,
    val kind: AbsenceKind,
    val note: String,
)

data class Student(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val className: String,
    val born: LocalDate,
    val interests: List<String>,
    val guardians: List<Guardian>,
    val absences: List<Absence>,
) {
    val name: String get() = "$firstName $lastName"
    val email: String get() = "${firstName.lowercase()}.${lastName.lowercase()}@school.example"
}
