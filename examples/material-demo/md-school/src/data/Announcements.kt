package com.softistx.material.school.data

import kotlin.time.Instant

data class Announcement(
    val id: Int,
    val title: String,
    val author: String,
    val body: String,
    val at: Instant,
    val pinned: Boolean = false,
)

object Announcements {
    val all: List<Announcement> =
        listOf(
            Announcement(
                1,
                "Parent evening on 5 November",
                "Principal Moreau",
                "Appointments open on the portal this Friday. Each slot lasts ten minutes and teachers will " +
                    "have the term 1 marks ready. Please book early: the popular afternoon slots go first, and " +
                    "a second evening will not be added this year.",
                ago(1800),
                pinned = true,
            ),
            Announcement(
                2,
                "Science fair project briefs",
                "Mr. Brandt",
                "Classes 8A and 8B hand in their project plans by Monday. Groups of three, one page, and " +
                    "a list of the materials you need from the lab.",
                ago(3200),
            ),
            Announcement(
                3,
                "Library closed on Wednesday",
                "Ms. Okonkwo",
                "The library is closed on Wednesday morning for stock-taking. Books due that day can be " +
                    "returned on Thursday without a fine.",
                ago(6100),
            ),
            Announcement(
                4,
                "Choir auditions",
                "Mr. Fontaine",
                "Auditions run on Tuesday and Thursday at lunch in the music room. No preparation needed.",
                ago(9000),
            ),
            Announcement(
                5,
                "New fire drill routes",
                "Facilities",
                "Updated evacuation maps are posted by every classroom door. Please walk your class through " +
                    "the new route this week.",
                ago(12000),
            ),
        )
}
