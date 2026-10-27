package com.softistx.material.school.data

import kotlinx.datetime.LocalDate

enum class GradeKind(
    val label: String,
) {
    Quiz("Quiz"),
    Exam("Exam"),
    Homework("Homework"),
    Project("Project"),
}

/** One mark out of 20. */
data class Grade(
    val id: Int,
    val studentId: Int,
    val subject: Subject,
    val score: Double,
    val weight: Int,
    val kind: GradeKind,
    val date: LocalDate,
    val comment: String = "",
)

/** Two marks per student and subject, from index arithmetic. */
object Grades {
    private val comments = listOf("Solid work", "Careless slips", "Great progress", "Needs revision", "Excellent", "")

    val all: List<Grade> =
        Students.all.flatMap { student ->
            Subject.entries.flatMapIndexed { s, subject ->
                List(2) { k ->
                    val n = student.id * 7 + s * 11 + k * 5
                    Grade(
                        id = student.id * 100 + s * 10 + k,
                        studentId = student.id,
                        subject = subject,
                        score = 6.0 + (n % 29) * 0.5,
                        weight = 1 + (n % 3),
                        kind = GradeKind.entries[(n + k) % GradeKind.entries.size],
                        date = LocalDate(2026, 9 + k, 8 + (n % 18)),
                        comment = comments[n % comments.size],
                    )
                }
            }
        }

    fun average(grades: List<Grade>): Double {
        val weights = grades.sumOf { it.weight }
        return if (weights == 0) 0.0 else grades.sumOf { it.score * it.weight } / weights
    }
}

/** "13.5" — one decimal, no locale surprises. */
fun Double.oneDecimal(): String {
    val tenths = kotlin.math.round(this * 10).toInt()
    return "${tenths / 10}.${tenths % 10}"
}
