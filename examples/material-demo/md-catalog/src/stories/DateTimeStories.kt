package com.strange.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.strange.material.datetime.Calendar
import com.strange.material.datetime.DateField
import com.strange.material.datetime.DateRangeField
import com.strange.material.datetime.TimeField
import com.strange.material.demo.storyGroup
import com.strange.material.theme.StrangeTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md)) {
                Calendar(value = date, onValueChange = { date = it })
            }
        }
    }
