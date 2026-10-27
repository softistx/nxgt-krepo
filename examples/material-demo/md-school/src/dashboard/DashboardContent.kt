package com.softistx.material.school.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.feedback.AnnouncementBar
import com.softistx.material.components.feedback.PromoBanner
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.common.isWide
import com.softistx.material.school.session.SchoolSession

/** Two columns on a wide window, one column of the same cards on a phone. */
@Composable
fun DashboardContent(
    data: DashboardData,
    session: SchoolSession,
) {
    var barVisible by remember { mutableStateOf(true) }
    if (barVisible) {
        AnnouncementBar(
            text = "Mock exams start on 9 November. Timetables are on the portal.",
            tone = Tone.Info,
            onDismiss = { barVisible = false },
        )
    }
    PromoBanner(text = "Parent evening on 5 November. Booking code", code = "PTM2026", tone = Tone.Success)
    StatsRow(session)
    BoxWithConstraints {
        val gap = Arrangement.spacedBy(StxTheme.spacing.md)
        if (maxWidth.isWide()) {
            Row(horizontalArrangement = gap, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = gap) { LeftColumn(data, session) }
                Column(Modifier.weight(1f), verticalArrangement = gap) { RightColumn(data, session) }
            }
        } else {
            Column(verticalArrangement = gap) {
                LeftColumn(data, session)
                RightColumn(data, session)
            }
        }
    }
}

@Composable
private fun LeftColumn(
    data: DashboardData,
    session: SchoolSession,
) {
    TodayCard(data.lessons, session)
    ClassesCarousel()
    TodoCard()
}

@Composable
private fun RightColumn(
    data: DashboardData,
    session: SchoolSession,
) {
    RecentGradesCard(data.recent, session)
    AnnouncementsCard(data.announcements)
}
