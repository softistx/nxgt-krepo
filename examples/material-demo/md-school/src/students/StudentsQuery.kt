package com.softistx.material.school.students

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.button.ViewMode
import com.softistx.material.components.data.SortDirection
import com.softistx.material.school.data.Grade
import com.softistx.material.school.data.Grades
import com.softistx.material.school.data.Student

val StudentSorts: List<String> = listOf("Name", "Class", "Average")

const val PageSize = 10

/** What the reader typed, ticked and sorted by on the student list. Applying it is one function. */
@Stable
class StudentsQuery {
    var text by mutableStateOf("")
    var classes by mutableStateOf(emptySet<String>())
    var sort by mutableStateOf<String?>(StudentSorts.first())
    var direction by mutableStateOf(SortDirection.Asc)
    var view by mutableStateOf(ViewMode.List)
    var page by mutableStateOf(0)

    val isFiltered: Boolean get() = text.isNotBlank() || classes.isNotEmpty()

    fun clear() {
        text = ""
        classes = emptySet()
        page = 0
    }

    fun apply(
        students: List<Student>,
        grades: List<Grade>,
    ): List<Student> {
        val byStudent = grades.groupBy { it.studentId }
        val matching =
            students.filter { s ->
                (classes.isEmpty() || s.className in classes) &&
                    (text.isBlank() || s.name.contains(text.trim(), ignoreCase = true))
            }
        val sorted =
            when (sort) {
                "Class" -> matching.sortedWith(compareBy<Student> { it.className }.thenBy { it.name })
                "Average" -> matching.sortedBy { Grades.average(byStudent[it.id].orEmpty()) }
                else -> matching.sortedBy { it.name }
            }
        return if (direction == SortDirection.Asc) sorted else sorted.reversed()
    }
}

@Composable
fun rememberStudentsQuery(): StudentsQuery = remember { StudentsQuery() }
