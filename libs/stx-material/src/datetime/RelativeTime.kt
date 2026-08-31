package com.strange.material.datetime

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Turns two instants into a short relative label: "Just now", "3 min ago", "Yesterday", or the
 * ISO date once a week has passed.
 *
 * [zone] is only used past the 24-hour mark, where "yesterday" is a calendar fact and not a
 * duration. Future values spell "in N min" rather than a negative ago.
 */
fun relativeTime(
    at: Instant,
    now: Instant,
    zone: TimeZone = TimeZone.UTC,
): String {
    val elapsed = now - at
    if (elapsed.isNegative()) {
        val ahead = elapsed.absoluteValue
        return when {
            ahead < 1.minutes -> "Just now"
            ahead < 60.minutes -> "in ${ahead.inWholeMinutes} min"
            ahead < 24.hours -> "in ${ahead.inWholeHours} h"
            else -> at.toLocalDateTime(zone).date.toString()
        }
    }
    return when {
        elapsed < 1.minutes -> {
            "Just now"
        }

        elapsed < 60.minutes -> {
            "${elapsed.inWholeMinutes} min ago"
        }

        elapsed < 24.hours -> {
            "${elapsed.inWholeHours} h ago"
        }

        else -> {
            val days = at.toLocalDateTime(zone).date.daysUntil(now.toLocalDateTime(zone).date)
            when (days) {
                1 -> "Yesterday"
                in 2..6 -> "$days days ago"
                else -> at.toLocalDateTime(zone).date.toString()
            }
        }
    }
}

/**
 * When something happened, in words. Material 3 has no relative time.
 *
 * [now] is passed in rather than read off a clock inside: a ticking label is a `LaunchedEffect`
 * the screen owns, and a snapshot test needs a frozen instant. Defaulting it here would freeze
 * it at first composition and never move.
 */
@Composable
fun RelativeTime(
    at: Instant,
    now: Instant,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.UTC,
) {
    Typography(
        text = relativeTime(at, now, zone),
        modifier = modifier,
        variant = TypographyVariant.Caption,
        emphasis = Emphasis.Medium,
    )
}
