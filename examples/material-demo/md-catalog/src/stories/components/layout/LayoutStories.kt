package com.softistx.material.demo.stories.components.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.layout.ListDetailLayout
import com.softistx.material.components.layout.ListDetailLayoutDefaults
import com.softistx.material.components.layout.LoadMoreButton
import com.softistx.material.components.layout.RefreshBox
import com.softistx.material.components.layout.ResponsiveGrid
import com.softistx.material.components.layout.ScrollToTop
import com.softistx.material.components.layout.SelectionBar
import com.softistx.material.demo.storyGroup
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography

val LayoutStories =
    storyGroup("Layout") {
        story("Responsive grid") { _ ->
            ResponsiveGrid(
                items = listOf("North", "East", "South", "West", "Centre"),
                modifier = Modifier.height(280.dp),
                minSize = 140.dp,
            ) { name ->
                Card { Typography(text = name) }
            }
        }

        story("List detail layout") { knobs ->
            var open by remember { mutableStateOf<Int?>(null) }
            val width = knobs.number("Offered width (dp)", 900f, 400f..900f, steps = 9)
            Box(Modifier.width(width.dp).height(320.dp)) {
                ListDetailLayout(
                    selected = open != null,
                    placeholder = { ListDetailLayoutDefaults.Placeholder("Select a row", it) },
                    list = { pane ->
                        LazyColumn(pane) {
                            items((1..8).toList()) { n ->
                                ListTile(title = "Row $n", onClick = { open = n })
                            }
                        }
                    },
                    detail = { pane ->
                        Column(pane) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Typography(text = "Detail of row $open", modifier = Modifier.weight(1f))
                                IconButton(icon = StxIcons.Close, description = "Back to the list", onClick = { open = null })
                            }
                        }
                    },
                )
            }
        }

        story("Scroll to top") { _ ->
            val listState = rememberLazyListState()
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                LazyColumn(state = listState) {
                    itemsIndexed((1..40).toList()) { _, n ->
                        ListTile(title = "Row $n")
                    }
                }
                ScrollToTop(listState)
            }
        }

        story("Load more") { knobs ->
            LoadMoreButton(
                hasMore = knobs.flag("Has more", true),
                loading = knobs.flag("Loading", false),
                onClick = {},
            )
        }

        story("Selection bar") { knobs ->
            SelectionBar(
                count = knobs.number("Count", 3f, 0f..12f, steps = 11).toInt(),
                onClear = {},
            ) {
                IconButton(icon = StxIcons.Delete, description = "Delete", onClick = {})
                IconButton(icon = StxIcons.Copy, description = "Export", onClick = {})
            }
        }

        story("Refresh box") { _ ->
            var refreshing by remember { mutableStateOf(false) }
            RefreshBox(
                refreshing = refreshing,
                onRefresh = { refreshing = true },
                modifier = Modifier.height(200.dp),
            ) {
                ListTile(title = "Pull to refresh", supporting = if (refreshing) "Refreshing…" else "Idle")
            }
        }
    }
