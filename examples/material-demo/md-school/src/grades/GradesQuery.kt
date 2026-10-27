package com.softistx.material.school.grades

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.data.SortDirection
import com.softistx.material.school.data.Grade
import com.softistx.material.school.data.Students
import com.softistx.material.school.data.Subject
import kotlinx.datetime.LocalDate

val GradeSorts: List<String> = listOf("Student", "Score", "Date")
val ClassFilters: List<String> = listOf("All") + Students.classNames
val SubjectTabs: List<String> = listOf("All") + Subject.entries.map { it.label }

/** The filters and the sort of the results table, and which grade the form is editing. */
@Stable
class GradesQuery {
    var classIndex by mutableStateOf(1)
    var subjectIndex by mutableStateOf(1)
    var sort by mutableStateOf<String?>(GradeSorts[1])
    var direction by mutableStateOf(SortDirection.Desc)
    var from by mutableStateOf<LocalDate?>(null)
    var to by mutableStateOf<LocalDate?>(null)
    var editing by mutableStateOf<Grade?>(null)
    var tab by mutableStateOf(0)

    fun apply(grades: List<Grade>): List<Grade> {
        val className = ClassFilters[classIndex].takeIf { classIndex > 0 }
        val subject = Subject.entries.getOrNull(subjectIndex - 1)
        val start = from
        val end = to
        val filtered =
            grades.filter { g ->
                val student = Students.byId(g.studentId)
                (className == null || student?.className == className) &&
                    (subject == null || g.subject == subject) &&
                    (start == null || g.date >= start) &&
                    (end == null || g.date <= end)
            }
        val sorted =
            when (sort) {
                "Student" -> filtered.sortedBy { Students.byId(it.studentId)?.name.orEmpty() }
                "Date" -> filtered.sortedBy { it.date }
                else -> filtered.sortedBy { it.score }
            }
        return if (direction == SortDirection.Asc) sorted else sorted.reversed()
    }
}

@Composable
fun rememberGradesQuery(): GradesQuery = remember { GradesQuery() }
