package com.softistx.material.school.data

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

data class Lesson(
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val subject: Subject,
    val className: String,
) {
    val teacher: String get() = subject.teacher
    val room: String get() = subject.room
    val time: String get() = "${start.hhmm()}-${end.hhmm()}"
}

fun LocalTime.hhmm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** A fixed week for each class. Wednesday afternoon is free for everybody. */
object Timetable {
    /** The filter value that means "the signed-in teacher's own lessons". */
    const val Mine: String = "My lessons"

    val options: List<String> = listOf(Mine) + Students.classNames

    private val slots =
        listOf(
            LocalTime(8, 30) to LocalTime(9, 25),
            LocalTime(9, 35) to LocalTime(10, 30),
            LocalTime(10, 45) to LocalTime(11, 40),
            LocalTime(11, 50) to LocalTime(12, 45),
            LocalTime(14, 0) to LocalTime(14, 55),
            LocalTime(15, 5) to LocalTime(16, 0),
        )

    private val week: List<Lesson> =
        Students.classNames.flatMapIndexed { c, className ->
            SchoolClockDays.flatMap { day ->
                slots.mapIndexedNotNull { index, (start, end) ->
                    if (day == DayOfWeek.WEDNESDAY && index >= 4) return@mapIndexedNotNull null
                    val subject = Subject.entries[(day.ordinal * 2 + index + c * 5) % Subject.entries.size]
                    Lesson(day, start, end, subject, className)
                }
            }
        }

    /** Lessons for [filter] (a class name, or [Mine]) on [day], earliest first. */
    fun lessons(
        filter: String,
        day: DayOfWeek,
    ): List<Lesson> =
        week
            .filter { it.day == day }
            .filter { if (filter == Mine) it.teacher == CurrentTeacher else it.className == filter }
            .sortedBy { it.start }

    private val SchoolClockDays get() = SchoolClock.teachingDays
}
