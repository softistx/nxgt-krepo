package com.softistx.material.school.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.feedback.ToasterState
import com.softistx.material.components.feedback.rememberToasterState
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
