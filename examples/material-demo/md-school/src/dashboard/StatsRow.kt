package com.softistx.material.school.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.content.Stat
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.data.Grades
import com.softistx.material.school.data.Students
import com.softistx.material.school.data.oneDecimal
import com.softistx.material.school.session.SchoolSession

private class StatSpec(
    val value: String,
    val label: String,
    val delta: String?,
    val tone: Tone,
)

/** Four figures: one row on a wide window, two rows of two on a phone. */
@Composable
fun StatsRow(session: SchoolSession) {
    val specs =
        listOf(
            StatSpec(Students.all.size.toString(), "Students", "${Students.classNames.size} classes", Tone.Info),
            StatSpec(Grades.average(session.grades).oneDecimal(), "Average mark", "+0.6 this term", Tone.Success),
            StatSpec("96%", "Attendance", "-1% this week", Tone.Warning),
            StatSpec(session.unread.toString(), "Unread messages", "${session.familiesWaiting} families waiting", Tone.Info),
        )
    BoxWithConstraints {
        val gap = Arrangement.spacedBy(StxTheme.spacing.sm)
        val perRow = if (maxWidth > StatsWideThreshold) 4 else 2
        Column(verticalArrangement = gap) {
            specs.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = gap) {
                    row.forEach { Stat(it.value, it.label, Modifier.weight(1f), it.delta, it.tone) }
                }
            }
        }
    }
}

private val StatsWideThreshold = 600.dp
