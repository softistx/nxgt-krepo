package com.softistx.material.school.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.feedback.LabeledProgress
import com.softistx.material.components.surface.Carousel
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.school.data.Grades
import com.softistx.material.school.data.Students
import com.softistx.material.school.data.oneDecimal

/** One card per class with its average mark and its attendance, swiped sideways. */
@Composable
fun ClassesCarousel() {
    SectionHeader(title = "Classes at a glance", supporting = "Average mark and attendance")
    val classes = Students.classNames
    Carousel(count = classes.size, modifier = Modifier.fillMaxWidth()) { index ->
        val name = classes[index]
        val ids = Students.all.filter { it.className == name }.map { it.id }.toSet()
        val average = Grades.average(Grades.all.filter { it.studentId in ids })
        Card(modifier = Modifier.fillMaxWidth()) {
            Typography(text = "Class $name", variant = TypographyVariant.TitleMedium)
            Typography(text = "${ids.size} students, average ${average.oneDecimal()}/20")
            LabeledProgress(progress = 0.9f + index * 0.02f, caption = "Attendance")
        }
    }
}
