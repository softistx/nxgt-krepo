package com.softistx.material.school.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.Stat
import com.softistx.material.components.data.Timeline
import com.softistx.material.components.data.TimelineItem
import com.softistx.material.components.feedback.LabeledProgress
import com.softistx.material.components.feedback.StatusDot
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.data.AbsenceKind
import com.softistx.material.school.data.Student
import com.softistx.material.school.data.pretty

private fun AbsenceKind.tone(): Tone =
    when (this) {
        AbsenceKind.Excused -> Tone.Info
        AbsenceKind.Unexcused -> Tone.Error
        AbsenceKind.Late -> Tone.Warning
    }

/** Absences and lateness this term, with the attendance rate they leave. */
@Composable
fun AttendanceTab(student: Student) {
    val absences = student.absences
    val rate = 1f - absences.size * 0.02f
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
            Stat("${absences.size}", "Absences", Modifier.weight(1f), tone = Tone.Warning)
            Stat("${absences.count { it.kind == AbsenceKind.Late }}", "Late", Modifier.weight(1f), tone = Tone.Info)
        }
        LabeledProgress(progress = rate, caption = "Attendance this term")
        Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            AbsenceKind.entries.forEach {
                StatusDot(tone = it.tone(), description = null)
                Typography(text = it.label)
            }
        }
        if (absences.isEmpty()) {
            EmptyState(title = "Perfect attendance", description = "No absence has been recorded this term.")
        } else {
            Timeline(
                items = absences.sortedBy { it.date }.map { TimelineItem(it.kind.label, it.date.pretty(), it.note) },
            )
        }
    }
}
