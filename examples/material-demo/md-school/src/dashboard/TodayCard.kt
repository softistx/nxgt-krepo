package com.softistx.material.school.dashboard

import androidx.compose.runtime.Composable
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.data.Timeline
import com.softistx.material.components.data.TimelineItem
import com.softistx.material.school.data.Lesson
import com.softistx.material.school.data.SchoolClock
import com.softistx.material.school.data.pretty
import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolSession

/** The signed-in teacher's lessons for today, in order, as a timeline. */
@Composable
fun TodayCard(
    lessons: List<Lesson>,
    session: SchoolSession,
) {
    Card {
        SectionHeader(
            title = "Today",
            supporting = SchoolClock.today.pretty(),
            action = {
                Button(
                    text = "Week",
                    onClick = { session.open(SchoolScreen.Timetable) },
                    variant = ButtonVariant.Link,
                )
            },
        )
        if (lessons.isEmpty()) {
            EmptyState(title = "No lessons today", description = "Enjoy the free day.")
        } else {
            Timeline(
                items =
                    lessons.map {
                        TimelineItem(
                            title = "${it.subject.label} with ${it.className}",
                            at = it.time,
                            body = it.room,
                        )
                    },
            )
        }
    }
}
