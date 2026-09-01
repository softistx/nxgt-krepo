package com.softistx.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.datetime.Calendar
import com.softistx.material.datetime.DateField
import com.softistx.material.datetime.DateRangeField
import com.softistx.material.datetime.RelativeTime
import com.softistx.material.datetime.TimeField
import com.softistx.material.demo.storyGroup
import com.softistx.material.text.Typography
import com.softistx.material.theme.StxTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

val DateTimeStories =
    storyGroup("Date and time") {
        story("Date field") { _ ->
            var date by remember { mutableStateOf<LocalDate?>(LocalDate(2026, 8, 31)) }
            DateField(value = date, onValueChange = { date = it })
        }

        story("Date range field") { _ ->
            var start by remember { mutableStateOf<LocalDate?>(LocalDate(2026, 8, 1)) }
            var end by remember { mutableStateOf<LocalDate?>(LocalDate(2026, 8, 31)) }
            DateRangeField(start = start, end = end, onValueChange = { s, e ->
                start = s
                end = e
            })
        }

        story("Time field") { _ ->
            var time by remember { mutableStateOf<LocalTime?>(LocalTime(14, 30)) }
            TimeField(value = time, onValueChange = { time = it })
        }

        story("Calendar") { _ ->
            var date by remember { mutableStateOf<LocalDate?>(LocalDate(2026, 8, 31)) }
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
                Calendar(value = date, onValueChange = { date = it })
            }
        }

        story("Relative time") { _ ->
            val now = Instant.fromEpochSeconds(1_777_766_400)
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                listOf(
                    30.minutes to "half an hour ago",
                    5.hours to "this morning",
                    1.days to "yesterday",
                    10.days to "last week",
                ).forEach { (ago, caption) ->
                    Column {
                        Typography(text = caption)
                        RelativeTime(at = now - ago, now = now)
                    }
                }
            }
        }
    }
