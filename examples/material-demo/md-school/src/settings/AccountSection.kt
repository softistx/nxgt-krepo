package com.softistx.material.school.settings

import androidx.compose.runtime.Composable
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonSize
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.data.Description
import com.softistx.material.components.data.DescriptionItem
import com.softistx.material.components.feedback.SkeletonCards
import com.softistx.material.components.feedback.StateHost
import com.softistx.material.components.media.PersonCard
import com.softistx.material.school.data.CURRENT_TEACHER
import com.softistx.material.school.session.DataState
import com.softistx.material.school.session.SchoolSession
import com.softistx.material.school.session.of

/** The signed-in teacher, read from the (pretend) server like the rest of the app. */
@Composable
fun AccountSection(session: SchoolSession) {
    Card {
        SectionHeader(title = "Account")
        StateHost(
            state = session.data.of(full = true, empty = false),
            onRetry = { session.data = DataState.Loaded },
            loading = { SkeletonCards(count = 1) },
        ) { linked ->
            if (!linked) {
                EmptyState(
                    title = "No account linked",
                    description = "Link your staff account to see your classes.",
                    action = {
                        Button(
                            text = "Link account",
                            onClick = { session.data = DataState.Loaded },
                            size = ButtonSize.Medium,
                        )
                    },
                )
                return@StateHost
            }
            PersonCard(name = "Elena Alvarez", supporting = "$CURRENT_TEACHER, Mathematics")
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
