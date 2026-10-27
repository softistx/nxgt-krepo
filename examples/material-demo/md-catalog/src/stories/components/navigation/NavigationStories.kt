package com.softistx.material.demo.stories.components.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.button.Fab
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.navigation.AdaptiveNavDisplay
import com.softistx.material.components.navigation.AppBar
import com.softistx.material.components.navigation.AppBarSize
import com.softistx.material.components.navigation.BottomBar
import com.softistx.material.components.navigation.Breadcrumb
import com.softistx.material.components.navigation.BreadcrumbItem
import com.softistx.material.components.navigation.FloatingToolbar
import com.softistx.material.components.navigation.ListDetail
import com.softistx.material.components.navigation.NavigationDestination
import com.softistx.material.components.navigation.NavigationSuite
import com.softistx.material.components.navigation.Search
import com.softistx.material.components.navigation.SegmentedControl
import com.softistx.material.components.navigation.Step
import com.softistx.material.components.navigation.StepFooter
import com.softistx.material.components.navigation.Stepper
import com.softistx.material.components.navigation.Tabs
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.demo.storyGroup
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

val NavigationStories =
    storyGroup("Navigation") {
        story("App bar") { knobs ->
            val size = knobs.enumChoice("Size", AppBarSize.Small)
            val behavior =
                if (size == AppBarSize.Medium || size == AppBarSize.Large) {
                    TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                } else {
                    TopAppBarDefaults.enterAlwaysScrollBehavior()
                }
            val collapse = knobs.flag("Collapse on scroll", false)
            Column(
                modifier =
                    Modifier
                        .height(420.dp)
                        .then(if (collapse) Modifier.nestedScroll(behavior.nestedScrollConnection) else Modifier),
            ) {
                AppBar(
                    title = knobs.text("Title", "Orders"),
                    size = size,
                    navigationIcon = StxIcons.Menu,
                    navigationDescription = "Open navigation",
                    onNavigation = {},
                    scrollBehavior = if (collapse) behavior else null,
                    actions = {
                        IconButton(
                            icon = StxIcons.Search,
                            description = "Search",
                            onClick = {},
                        )
                    },
                )
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    repeat(24) { Typography("Order ${it + 1}", modifier = Modifier.padding(StxTheme.spacing.md)) }
                }
            }
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
                icons = if (knobs.flag("Icons", false)) listOf(StxIcons.ViewList, StxIcons.ViewGrid, StxIcons.Calendar) else null,
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
            val current = knobs.number("Current step", 1f, 0f..3f, steps = 2).toInt()
            Stepper(
                steps =
                    listOf(
                        Step("Account", "Email and password"),
                        Step("Profile", "Name and photo"),
                        Step("Plan", "Monthly or yearly"),
                        Step("Review", "Confirm and pay"),
                    ),
                current = current,
                onStep = { knobs.set("Current step", it.toFloat()) },
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
