package com.softistx.material.school.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.button.ConfirmButton
import com.softistx.material.components.content.ExpandableText
import com.softistx.material.components.content.Rating
import com.softistx.material.components.data.Description
import com.softistx.material.components.data.DescriptionItem
import com.softistx.material.components.form.DangerZone
import com.softistx.material.components.form.InlineEdit
import com.softistx.material.components.form.TagField
import com.softistx.material.components.media.AvatarGroup
import com.softistx.material.components.media.AvatarItem
import com.softistx.material.components.surface.Disclosure
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.common.say
import com.softistx.material.school.data.Student
import com.softistx.material.school.data.Students
import com.softistx.material.school.data.pretty
import com.softistx.material.school.session.SchoolSession

/** Who the student is, with the few fields a teacher edits in place. */
@Composable
fun ProfileTab(
    student: Student,
    session: SchoolSession,
) {
    var nickname by remember(student.id) { mutableStateOf(student.firstName) }
    var tags by remember(student.id) { mutableStateOf(student.interests) }
    var behaviour by remember(student.id) { mutableStateOf(3 + student.id % 3) }
    var notes by remember(student.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
        Description(
            items =
                listOf(
                    DescriptionItem("Student ID", "S-${1000 + student.id}"),
                    DescriptionItem("Class", student.className),
                    DescriptionItem("Born", student.born.pretty()),
                    DescriptionItem("Email", student.email),
                ),
        )
        Typography(text = "Preferred name", variant = TypographyVariant.LabelMedium)
        InlineEdit(value = nickname, onValueChange = { nickname = it }, placeholder = "Add a preferred name")
        TagField(tags = tags, onTagsChange = { tags = it }, label = "Interests")
        Typography(text = "Behaviour this term", variant = TypographyVariant.LabelMedium)
        Rating(value = behaviour, onChange = { behaviour = it })
        Disclosure(title = "Health notes", expanded = notes, onExpandedChange = { notes = it }) {
            Typography(text = "No allergies on file. Asthma inhaler kept in the nurse's office.")
        }
        Typography(text = "Report comment", variant = TypographyVariant.LabelMedium)
        ExpandableText(
            text =
                "${student.firstName} contributes well in class and works steadily. Written work is " +
                    "careful, and the next step is to check answers before handing them in. Attendance " +
                    "and punctuality are good, and ${student.firstName} gets on well with the whole group.",
            collapsedLines = 2,
        )
        Typography(text = "Classmates", variant = TypographyVariant.LabelMedium)
        AvatarGroup(
            items =
                Students.all
                    .filter { it.className == student.className && it.id != student.id }
                    .map { AvatarItem(it.name) },
            max = 5,
        )
        DangerZone(text = "Removing a student deletes their marks and messages from this class.") {
            ConfirmButton(
                text = "Unenrol student",
                onConfirm = {
                    session.toaster.say("${student.name} unenrolled (demo only)")
                    session.studentId = null
                },
            )
        }
    }
}
