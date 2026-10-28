package com.softistx.material.demo.stories.components.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.navigation.AdaptiveNavDisplay
import com.softistx.material.components.navigation.Breadcrumb
import com.softistx.material.components.navigation.BreadcrumbItem
import com.softistx.material.components.navigation.ListDetail
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/** The DetailNavigation stories of the Navigation group. */
internal fun StoryGroupBuilder.detailNavigationStories() {
    story("Breadcrumb") { knobs ->
        val deep = knobs.flag("Deep trail (overflow)", true)
        val items =
            if (deep) {
                listOf(
                    BreadcrumbItem("Home", onClick = {}),
                    BreadcrumbItem("Workspace", onClick = {}),
                    BreadcrumbItem("Projects", onClick = {}),
                    BreadcrumbItem("Northwind", onClick = {}),
                    BreadcrumbItem("Orders"),
                )
            } else {
                listOf(
                    BreadcrumbItem("Home", onClick = {}),
                    BreadcrumbItem("Orders"),
                )
            }
        Breadcrumb(items = items)
    }

    story("List detail") { _ ->
        val backStack = remember { mutableStateListOf<MailKey>(MailInbox) }
        AdaptiveNavDisplay(
            backStack = backStack,
            modifier = Modifier.height(420.dp),
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            entryProvider =
                entryProvider {
                    entry<MailInbox>(metadata = ListDetail.list("Pick a conversation")) {
                        Column {
                            SampleMail.forEach { mail ->
                                ListTile(
                                    title = mail.from,
                                    supporting = mail.subject,
                                    onClick = { backStack.add(MailMessage(mail.id)) },
                                )
                            }
                        }
                    }
                    entry<MailMessage>(metadata = ListDetail.detail()) { key ->
                        val mail = SampleMail.first { it.id == key.id }
                        Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                            Typography(text = mail.subject, variant = TypographyVariant.TitleMedium)
                            Typography(text = mail.body)
                        }
                    }
                },
        )
    }
}

private sealed interface MailKey

private data object MailInbox : MailKey

private data class MailMessage(
    val id: String,
) : MailKey

private data class Mail(
    val id: String,
    val from: String,
    val subject: String,
    val body: String,
)

private val SampleMail =
    listOf(
        Mail("1", "Amara Diallo", "Payout delayed", "The 14:00 batch is still with the bank."),
        Mail("2", "Jonas Weber", "New supplier", "Northwind asked to be added to the roster."),
        Mail("3", "Priya Raman", "Refund #4412", "Customer was charged twice for the same order."),
    )
