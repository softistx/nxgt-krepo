package com.softistx.material.school.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.CardVariant
import com.softistx.material.components.feedback.StatusDot
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.data.Lesson
import com.softistx.material.school.data.SchoolClock
import kotlinx.datetime.LocalDate

/** True while [lesson] is in progress at the showcase's fixed "now". */
fun Lesson.isLive(date: LocalDate): Boolean = date == SchoolClock.today && SchoolClock.time >= start && SchoolClock.time < end

/** One lesson: subject, time, and optionally the room and teacher. A dot marks the one in progress. */
@Composable
fun LessonCard(
    lesson: Lesson,
    date: LocalDate,
    showRooms: Boolean,
    modifier: Modifier = Modifier,
) {
    val live = lesson.isLive(date)
    val variant = if (live) CardVariant.Elevated else CardVariant.Filled
    Card(modifier = modifier.fillMaxWidth(), variant = variant) {
        Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            if (live) StatusDot(tone = Tone.Success, description = "In progress")
            Typography(text = lesson.subject.label, variant = TypographyVariant.TitleSmall, maxLines = 1)
        }
        Typography(text = lesson.time, emphasis = Emphasis.Medium)
        Typography(text = "Class ${lesson.className}", emphasis = Emphasis.Medium)
        if (showRooms) {
            Typography(
                text = "${lesson.room}, ${lesson.teacher}",
                variant = TypographyVariant.BodySmall,
                emphasis = Emphasis.Subtle,
            )
        }
    }
}
