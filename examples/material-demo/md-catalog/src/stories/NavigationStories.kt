package com.strange.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import com.strange.material.button.Button
import com.strange.material.button.ButtonVariant
import com.strange.material.button.IconButton
import com.strange.material.demo.knobs.enumChoice
import com.strange.material.demo.storyGroup
import com.strange.material.display.ListTile
import com.strange.material.icon.StrangeIcons
import com.strange.material.navigation.AdaptiveNavDisplay
import com.strange.material.navigation.AppBar
import com.strange.material.navigation.AppBarSize
import com.strange.material.navigation.Breadcrumb
import com.strange.material.navigation.BreadcrumbItem
import com.strange.material.navigation.FloatingToolbar
import com.strange.material.navigation.ListDetail
import com.strange.material.navigation.NavigationDestination
import com.strange.material.navigation.NavigationSuite
import com.strange.material.navigation.Search
import com.strange.material.navigation.SegmentedControl
import com.strange.material.navigation.Step
import com.strange.material.navigation.Stepper
import com.strange.material.navigation.Tabs
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

val NavigationStories =
    storyGroup("Navigation") {
        story("App bar") { knobs ->
            AppBar(
                title = knobs.text("Title", "Orders"),
                size = knobs.enumChoice("Size", AppBarSize.Small),
                navigationIcon = StrangeIcons.Menu,
                navigationDescription = "Open navigation",
                onNavigation = {},
                actions = {
                    IconButton(
                        icon = StrangeIcons.Search,
                        description = "Search",
                        onClick = {},
                    )
                },
            )
        }

        story("Search") { knobs ->
            var query by remember { mutableStateOf("") }
            var active by remember { mutableStateOf(false) }
            val names = listOf("Amara Diallo", "Jonas Weber", "Priya Raman", "Chen Wei")
            Search(
                query = query,
                onQueryChange = { query = it },
                placeholder = knobs.text("Placeholder", "Search people"),
                active = active,
                onActiveChange = { active = it },
            ) {
                names.filter { it.contains(query, ignoreCase = true) }.forEach { name ->
                    ListTile(title = name, onClick = { active = false })
                }
            }
        }

        story("Navigation suite") { knobs ->
            var selected by remember { mutableIntStateOf(0) }
            val destinations =
                listOf(
                    NavigationDestination("Home", StrangeIcons.Home),
                    NavigationDestination(
                        "Inbox",
                        StrangeIcons.Inbox,
                        badge = "3",
                        badgeTone = Tone.Info,
                        chip = "Live",
                    ),
                    NavigationDestination("People", StrangeIcons.Person),
                    NavigationDestination("Search", StrangeIcons.Search),
                )
            NavigationSuite(
                destinations = destinations,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.height(420.dp),
                primaryAction =
                    if (knobs.flag("Primary action", true)) {
                        { Button(text = "New", onClick = {}) }
                    } else {
                        null
                    },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Typography(
                        text = destinations[selected].label,
                        variant = TypographyVariant.HeadlineSmall,
                    )
                }
            }
        }

        story("Tabs") { knobs ->
            var selected by remember { mutableIntStateOf(0) }
            val labels = listOf("Paid", "Pending", "Refunded", "Disputed")
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md)) {
                Tabs(
                    labels = labels,
                    selected = selected,
                    onSelect = { selected = it },
                    scrollable = knobs.flag("Scrollable", false),
                    enabled = knobs.flag("Enabled", true),
                )
                Typography(text = "${labels[selected]} orders")
            }
        }

        story("Segmented control") { knobs ->
            var selected by remember { mutableIntStateOf(0) }
            SegmentedControl(
                options = listOf("Day", "Week", "Month"),
                selected = selected,
                onSelect = { selected = it },
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("Floating toolbar") { knobs ->
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                FloatingToolbar(expanded = knobs.flag("Expanded", true)) {
                    IconButton(icon = StrangeIcons.Edit, description = "Edit", onClick = {}, variant = ButtonVariant.Ghost)
                    IconButton(icon = StrangeIcons.Delete, description = "Delete", onClick = {}, variant = ButtonVariant.Ghost)
                    IconButton(icon = StrangeIcons.Add, description = "Add", onClick = {}, variant = ButtonVariant.Ghost)
                }
            }
        }

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
                            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                                Typography(text = mail.subject, variant = TypographyVariant.TitleMedium)
                                Typography(text = mail.body)
                            }
                        }
                    },
            )
        }

        story("Stepper") { knobs ->
            var current by remember { mutableIntStateOf(1) }
            Stepper(
                steps =
                    listOf(
                        Step("Account", "Email and password"),
                        Step("Profile", "Name and photo"),
                        Step("Plan", "Monthly or yearly"),
                        Step("Review", "Confirm and pay"),
                    ),
                current = current,
                onStep = { current = it },
                collapseBelow = if (knobs.flag("Force vertical", false)) 10_000.dp else 520.dp,
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
