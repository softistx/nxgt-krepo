package com.softistx.material.school.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.feedback.ToasterState
import com.softistx.material.components.feedback.rememberToasterState
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.data.Conversations
import com.softistx.material.school.data.Grade
import com.softistx.material.school.data.Grades

/**
 * The state every screen shares: where the reader is, what they chose in settings, and the marks
 * they have entered. Screens take the session and read what they need, so a test builds one with a
 * [SchoolStart] and hands it to the screen it wants to look at.
 */
@Stable
class SchoolSession(
    initialScreen: SchoolScreen,
    start: SchoolStart,
    val toaster: ToasterState,
) {
    var screen by mutableStateOf(initialScreen)
    var data by mutableStateOf(start.data)
    var studentId by mutableStateOf(start.studentId)
    var conversationId by mutableStateOf(start.conversationId)
    var paletteOpen by mutableStateOf(false)
    val preferences = Preferences(start.colorMode, start.animated)
    val grades = mutableStateListOf<Grade>().apply { addAll(Grades.all) }
    val archived = mutableStateListOf<Int>()

    /** Conversations with something unread, archived ones left out. */
    private val unreadConversations by derivedStateOf {
        Conversations.all.filter { it.id !in archived && it.unread > 0 }
    }

    /** Unread messages across the conversations that are not archived. */
    val unread: Int by derivedStateOf { unreadConversations.sumOf { it.unread } }

    /** Guardian conversations waiting for an answer. */
    val familiesWaiting: Int by derivedStateOf { unreadConversations.count { it.studentId != null } }

    fun finishRefresh() = toaster.show("Dashboard is up to date", Tone.Success)

    fun finishSync() = toaster.show("Synced with the school server", Tone.Success)

    fun open(screen: SchoolScreen) {
        this.screen = screen
    }

    fun openStudent(id: Int) {
        studentId = id
        screen = SchoolScreen.Students
    }
}

@Composable
fun rememberSchoolSession(
    initialScreen: SchoolScreen,
    start: SchoolStart,
): SchoolSession {
    val toaster = rememberToasterState()
    return remember { SchoolSession(initialScreen, start, toaster) }
}
