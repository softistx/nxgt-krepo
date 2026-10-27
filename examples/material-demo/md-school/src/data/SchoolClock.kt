package com.softistx.material.school.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The one "now" every screen reads. It is a constant, not `Clock.System`, so a screenshot taken
 * today and one taken next year show the same school day.
 */
object SchoolClock {
    val today: LocalDate = LocalDate(2026, 10, 26)
    val time: LocalTime = LocalTime(10, 5)
    val now: Instant = today.atTime(time).toInstant(TimeZone.UTC)

    /** Monday of the week [date] falls in. */
    fun weekStart(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)

    /** The five teaching days, in order. */
    val teachingDays: List<DayOfWeek> = DayOfWeek.entries.take(5)
}

/** An instant [minutesAgo] before [SchoolClock.now]. */
fun ago(minutesAgo: Int): Instant = SchoolClock.now - minutesAgo.minutes
