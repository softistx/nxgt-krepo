package com.softistx.material.school.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.Comment
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.PinBar
import com.softistx.material.components.content.Reaction
import com.softistx.material.components.content.ReactionBar
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.content.toggleReaction
import com.softistx.material.components.datetime.RelativeTime
import com.softistx.material.components.layout.LoadMoreButton
import com.softistx.material.school.data.Announcement
import com.softistx.material.school.data.SchoolClock

/** The pinned announcement on top, then the rest as comments with a reaction each. */
@Composable
fun AnnouncementsCard(announcements: List<Announcement>) {
    var shown by remember { mutableStateOf(2) }
    Card {
        SectionHeader(title = "Announcements")
        if (announcements.isEmpty()) {
            EmptyState(title = "Nothing posted", description = "Staff announcements appear here.")
        }
        announcements.firstOrNull { it.pinned }?.let { PinBar(text = it.title) }
        announcements.filter { !it.pinned }.take(shown).forEach { AnnouncementRow(it) }
        LoadMoreButton(
            hasMore = shown < announcements.count { !it.pinned },
            loading = false,
            onClick = { shown += 2 },
        )
    }
}

@Composable
private fun AnnouncementRow(announcement: Announcement) {
    var likes by remember { mutableStateOf(2 to false) }
    Comment(
        name = announcement.author,
        text = "${announcement.title}. ${announcement.body}",
        supporting = null,
        trailing = {
            ReactionBar(
                reactions =
                    listOf(
                        Reaction("Like", likes.first, likes.second) {
                            likes = toggleReaction(likes.first, likes.second)
                        },
                    ),
            )
        },
    )
    RelativeTime(at = announcement.at, now = SchoolClock.now)
}
