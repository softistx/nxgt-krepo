package com.softistx.material.school.data

/** The six subjects the showcase teaches. A teacher owns each one. */
enum class Subject(
    val label: String,
    val teacher: String,
    val room: String,
) {
    Mathematics("Mathematics", "Ms. Alvarez", "R101"),
    Physics("Physics", "Mr. Brandt", "Lab 2"),
    English("English", "Ms. Okonkwo", "R204"),
    History("History", "Mr. Lindqvist", "R210"),
    Biology("Biology", "Ms. Haddad", "Lab 1"),
    Art("Art", "Mr. Fontaine", "Studio"),
}

/** The signed-in user of the showcase. */
const val CurrentTeacher: String = "Ms. Alvarez"
