package com.softistx.material.school.settings

import androidx.compose.runtime.Composable
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.data.Description
import com.softistx.material.components.data.DescriptionItem
import com.softistx.material.components.media.PersonCard
import com.softistx.material.school.common.SkeletonCards
import com.softistx.material.school.common.StateHost
import com.softistx.material.school.data.CurrentTeacher
import com.softistx.material.school.session.DataState
import com.softistx.material.school.session.SchoolSession
import com.softistx.material.school.session.of

/** The signed-in teacher, read from the (pretend) server like the rest of the app. */
@Composable
fun AccountSection(session: SchoolSession) {
    Card {
        SectionHeader(title = "Account")
        StateHost(
            state = session.data.of(true, true),
            onRetry = { session.data = DataState.Loaded },
            loading = { SkeletonCards(count = 1) },
        ) {
            PersonCard(name = "Elena Alvarez", supporting = "$CurrentTeacher, Mathematics")
            Description(
                items =
                    listOf(
                        DescriptionItem("Classes", "7A, 7B, 8B"),
                        DescriptionItem("Staff email", "e.alvarez@school.example"),
                    ),
            )
        }
    }
}
