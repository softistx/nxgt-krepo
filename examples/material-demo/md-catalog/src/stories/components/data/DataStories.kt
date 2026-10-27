package com.softistx.material.demo.stories.components.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.data.CommandItem
import com.softistx.material.components.data.CommandPalette
import com.softistx.material.components.data.DataTable
import com.softistx.material.components.data.Description
import com.softistx.material.components.data.DescriptionItem
import com.softistx.material.components.data.EntityHeader
import com.softistx.material.components.data.Pagination
import com.softistx.material.components.data.SortControl
import com.softistx.material.components.data.SortDirection
import com.softistx.material.components.data.TableColumn
import com.softistx.material.components.data.TableSort
import com.softistx.material.components.data.Timeline
import com.softistx.material.components.data.TimelineItem
import com.softistx.material.demo.storyGroup
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme

private data class OrderRow(
    val id: String,
    val customer: String,
    val total: String,
)

val DataStories =
    storyGroup("Data") {
        story("Command palette") { _ ->
            var open by remember { mutableStateOf(false) }
            var query by remember { mutableStateOf("") }
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                Button(text = "Open palette", onClick = { open = true })
                CommandPalette(
                    visible = open,
                    query = query,
                    onQueryChange = { query = it },
                    onDismiss = { open = false },
                    items =
                        listOf(
                            CommandItem("New order", onRun = {}, group = "Orders", shortcut = "⌘N"),
                            CommandItem("Export CSV", onRun = {}, group = "Orders"),
                            CommandItem("Go to settings", onRun = {}, group = "App", shortcut = "⌘,"),
                        ),
                )
            }
        }

        story("Data table") { knobs ->
            var sort by remember { mutableStateOf<TableSort?>(TableSort(1)) }
            val sorted =
                listOf(
                    OrderRow("4412", "Amara Diallo", "€120.00"),
                    OrderRow("4413", "Jonas Weber", "€64.50"),
                    OrderRow("4414", "Priya Raman", "€210.00"),
                ).sortedBy { row -> listOf(row.id, row.customer, row.total)[sort?.column ?: 0] }
                    .let { if (sort?.direction == SortDirection.Desc) it.reversed() else it }
            DataTable(
                columns =
                    listOf(
                        TableColumn("Order") { it.id },
                        TableColumn("Customer") { it.customer },
                        TableColumn("Total") { it.total },
                    ),
                rows = sorted,
                collapseBelow = if (knobs.flag("Force cards", false)) 10_000.dp else 600.dp,
                sort = sort,
                onSortChange = { sort = it },
                onRowClick = {},
            )
        }

        story("Description") { _ ->
            Description(
                items =
                    listOf(
                        DescriptionItem("Status", "Paid"),
                        DescriptionItem("Placed", "31 Aug 2026"),
                        DescriptionItem("Total", "€120.00"),
                    ),
            )
        }

        story("Entity header") { _ ->
            EntityHeader(
                title = "Amara Diallo",
                supporting = "Customer since 2024",
                leading = { Icon(icon = StxIcons.Person, description = null, size = IconSize.XLarge) },
                actions = { Button(text = "Message", onClick = {}, variant = ButtonVariant.Ghost) },
            )
        }

        story("Pagination") { _ ->
            var page by remember { mutableIntStateOf(1) }
            Pagination(
                hasPrevious = page > 1,
                hasNext = page < 3,
                onPrevious = { page-- },
                onNext = { page++ },
                label = "Page $page",
            )
        }

        story("Sort control") { _ ->
            var selected by remember { mutableStateOf("Customer") }
            var direction by remember { mutableStateOf(SortDirection.Asc) }
            SortControl(
                options = listOf("Customer", "Total", "Status"),
                selected = selected,
                direction = direction,
                onSelectedChange = { selected = it },
                onDirectionChange = { direction = it },
            )
        }

        story("Timeline") { _ ->
            Timeline(
                items =
                    listOf(
                        TimelineItem("Order placed", "09:12", "Amara checked out."),
                        TimelineItem("Paid", "09:13", "Card ending 4242."),
                        TimelineItem("Shipped", "14:02"),
                    ),
            )
        }
    }
