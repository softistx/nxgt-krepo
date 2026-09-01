package com.softistx.material.demo.stories

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
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonVariant
import com.softistx.material.button.Fab
import com.softistx.material.button.IconButton
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.demo.storyGroup
import com.softistx.material.display.ListTile
import com.softistx.material.icon.StxIcons
import com.softistx.material.navigation.AdaptiveNavDisplay
import com.softistx.material.navigation.AppBar
import com.softistx.material.navigation.AppBarSize
import com.softistx.material.navigation.BottomBar
import com.softistx.material.navigation.Breadcrumb
import com.softistx.material.navigation.BreadcrumbItem
import com.softistx.material.navigation.FloatingToolbar
import com.softistx.material.navigation.ListDetail
import com.softistx.material.navigation.NavigationDestination
import com.softistx.material.navigation.NavigationSuite
import com.softistx.material.navigation.Search
import com.softistx.material.navigation.SegmentedControl
import com.softistx.material.navigation.Step
import com.softistx.material.navigation.StepFooter
import com.softistx.material.navigation.Stepper
import com.softistx.material.navigation.Tabs
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme
import com.softistx.material.theme.Tone

val NavigationStories =
    storyGroup("Navigation") {
        story("App bar") { knobs ->
            AppBar(
                title = knobs.text("Title", "Orders"),
                size = knobs.enumChoice("Size", AppBarSize.Small),
                navigationIcon = StxIcons.Menu,
                navigationDescription = "Open navigation",
                onNavigation = {},
                actions = {
                    IconButton(
                        icon = StxIcons.Search,
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
                    NavigationDestination("Home", StxIcons.Home),
                    NavigationDestination(
                        label = "Inbox",
                        icon = StxIcons.Inbox,
                        supporting = "Amara, Jonas",
                        badge = "3",
                        badgeTone = Tone.Info,
                    ),
                    NavigationDestination(
                        label = "Drafts",
                        icon = StxIcons.Edit,
                        unread = true,
                    ),
                    NavigationDestination(
                        label = "Amara Diallo",
                        icon = StxIcons.Person,
                        avatar = true,
                        supporting = "You",
                    ),
                    NavigationDestination(
                        label = "Reports",
                        icon = StxIcons.Search,
                        chip = "Beta",
                        shortcut = "⌘R",
                        section = "Workspace",
                    ),
                    NavigationDestination(
                        label = "Sync",
                        icon = StxIcons.Schedule,
                        busy = true,
                        tone = Tone.Info,
                    ),
                    NavigationDestination(
                        label = "Settings",
                        icon = StxIcons.Menu,
                        shortcut = "⌘,",
                        section = "Account",
                    ),
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
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
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
                    IconButton(icon = StxIcons.Edit, description = "Edit", onClick = {}, variant = ButtonVariant.Ghost)
                    IconButton(icon = StxIcons.Delete, description = "Delete", onClick = {}, variant = ButtonVariant.Ghost)
                    IconButton(icon = StxIcons.Add, description = "Add", onClick = {}, variant = ButtonVariant.Ghost)
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
                            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
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

        story("Bottom bar") { _ ->
            BottomBar(
                fab = { Fab(icon = StxIcons.Add, description = "New order", onClick = {}) },
            ) {
                IconButton(icon = StxIcons.Edit, description = "Edit", onClick = {})
                IconButton(icon = StxIcons.Search, description = "Search", onClick = {})
                IconButton(icon = StxIcons.Delete, description = "Delete", onClick = {})
            }
        }

        story("Step footer") { knobs ->
            StepFooter(
                onNext = {},
                onBack = if (knobs.flag("Show back", true)) ({ }) else null,
                busy = knobs.flag("Busy", false),
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
