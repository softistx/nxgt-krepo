package com.softistx.material.school.students

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.feedback.StateHost
import com.softistx.material.components.feedback.UiState
import com.softistx.material.components.layout.ListDetailLayout
import com.softistx.material.components.layout.ListDetailLayoutDefaults
import com.softistx.material.school.common.ScreenFrame
import com.softistx.material.school.data.Student
import com.softistx.material.school.data.Students
import com.softistx.material.school.session.DataState
import com.softistx.material.school.session.SchoolSession
import com.softistx.material.school.session.ofList

/** A searchable list of students; on a wide window the selected one opens beside it. */
@Composable
fun StudentsScreen(session: SchoolSession) {
    val query = rememberStudentsQuery()
    val state: UiState<List<Student>> = session.data.ofList(Students.all)
    val selected = session.studentId?.let(Students::byId)
    ScreenFrame(title = "Students", session = session, scroll = false) {
        ListDetailLayout(
            selected = selected != null,
            modifier = Modifier.weight(1f),
            placeholder = { ListDetailLayoutDefaults.Placeholder("Select a student", it) },
            list = { modifier ->
                StateHost(
                    state = state,
                    onRetry = { session.data = DataState.Loaded },
                    modifier = modifier,
                ) { students -> StudentList(students, query, session, Modifier.fillMaxSize()) }
            },
            detail = { modifier -> selected?.let { StudentDetail(it, session, modifier) } },
        )
    }
}
