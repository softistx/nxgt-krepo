package com.softistx.material.components.datetime

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.LocalStxStrings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Turns two instants into a short relative label: strings.justNow, "3 min ago", "Yesterday", or the
 * ISO date once a week has passed.
 *
 * [zone] is only used past the 24-hour mark, where "yesterday" is a calendar fact and not a
 * duration. Future values spell "in N min" rather than a negative ago. The words come from
 * [strings], English unless the caller passes another catalogue; [RelativeTime] passes the theme's.
 */
fun relativeTime(
    at: Instant,
    now: Instant,
    zone: TimeZone = TimeZone.UTC,
    strings: StxStrings = StxStrings.En,
): String {
    val elapsed = now - at
    if (elapsed.isNegative()) {
        val ahead = elapsed.absoluteValue
        return when {
            ahead < 1.minutes -> strings.justNow
            ahead < 60.minutes -> strings.inMinutes(ahead.inWholeMinutes.toInt())
            ahead < 24.hours -> strings.inHours(ahead.inWholeHours.toInt())
            else -> at.toLocalDateTime(zone).date.toString()
        }
    }
    return when {
        elapsed < 1.minutes -> {
            strings.justNow
        }

        elapsed < 60.minutes -> {
            strings.minutesAgo(elapsed.inWholeMinutes.toInt())
        }

        elapsed < 24.hours -> {
            strings.hoursAgo(elapsed.inWholeHours.toInt())
        }

        else -> {
            val days = at.toLocalDateTime(zone).date.daysUntil(now.toLocalDateTime(zone).date)
            when (days) {
                1 -> strings.yesterday
                in 2..6 -> strings.daysAgo(days)
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
 * it at first composition and never move. [strings] defaults to the theme's catalogue.
 */
@Composable
fun RelativeTime(
    at: Instant,
    now: Instant,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.UTC,
    strings: StxStrings = LocalStxStrings.current,
) {
    Typography(
        text = relativeTime(at, now, zone, strings),
        modifier = modifier,
        variant = TypographyVariant.Caption,
        emphasis = Emphasis.Medium,
    )
}
