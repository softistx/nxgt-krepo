package com.softistx.material.school.data

import kotlinx.datetime.LocalDate

/** Thirty students, built from three name lists and index arithmetic so nothing is random. */
object Students {
    private val firstNames =
        listOf(
            "Amara",
            "Liam",
            "Noor",
            "Mateo",
            "Yuki",
            "Zoe",
            "Omar",
            "Ines",
            "Kofi",
            "Lena",
            "Arjun",
            "Chloe",
            "Mehdi",
            "Sofia",
            "Ethan",
            "Aiko",
            "Tariq",
            "Maya",
            "Lucas",
            "Fatou",
            "Nina",
            "Diego",
            "Hana",
            "Samuel",
            "Priya",
            "Jonas",
            "Leila",
            "Oscar",
            "Mina",
            "Felix",
        )
    private val lastNames =
        listOf(
            "Okafor",
            "Martin",
            "Haddad",
            "Garcia",
            "Tanaka",
            "Brown",
            "Khalil",
            "Rossi",
            "Mensah",
            "Novak",
            "Sharma",
            "Dubois",
            "Benali",
            "Silva",
            "Walker",
            "Sato",
            "Rahman",
            "Cohen",
            "Moreau",
            "Diallo",
            "Larsen",
            "Torres",
            "Kim",
            "Adams",
            "Patel",
            "Weber",
            "Amrani",
            "Nilsson",
            "Park",
            "Costa",
        )
    private val interests =
        listOf("Chess", "Football", "Choir", "Robotics", "Drawing", "Swimming", "Reading", "Theatre", "Coding")
    private val classes = listOf("7A" to 8, "7B" to 7, "8A" to 8, "8B" to 7)

    val classNames: List<String> = classes.map { it.first }

    val all: List<Student> = List(30) { build(it) }

    fun byId(id: Int): Student? = all.firstOrNull { it.id == id }

    private fun classOf(index: Int): String {
        var rest = index
        for ((name, size) in classes) {
            if (rest < size) return name
            rest -= size
        }
        return classes.last().first
    }

    private fun build(index: Int): Student {
        val first = firstNames[index]
        val last = lastNames[(index * 7 + 3) % lastNames.size]
        val className = classOf(index)
        val year = if (className.startsWith("7")) 2014 else 2013
        return Student(
            id = index + 1,
            firstName = first,
            lastName = last,
            className = className,
            born = LocalDate(year, 1 + (index * 5) % 12, 1 + (index * 3) % 27),
            interests = listOf(interests[index % interests.size], interests[(index * 4 + 2) % interests.size]).distinct(),
            guardians = guardians(last, index),
            absences = absences(index),
        )
    }

    private fun guardians(
        last: String,
        index: Int,
    ): List<Guardian> =
        listOf(
            Guardian("Claire $last", "Mother", "+1 555 01${10 + index}", "claire.${last.lowercase()}@mail.example"),
            Guardian("Daniel $last", "Father", "+1 555 02${10 + index}", "daniel.${last.lowercase()}@mail.example"),
        )

    private fun absences(index: Int): List<Absence> =
        List(index % 4) { k ->
            val kind = AbsenceKind.entries[(index + k) % AbsenceKind.entries.size]
            Absence(
                date = LocalDate(2026, 9 + k % 2, 7 + (index + k * 4) % 20),
                kind = kind,
                note = if (kind == AbsenceKind.Late) "Arrived after the bell" else "Note from a guardian",
            )
        }
}
