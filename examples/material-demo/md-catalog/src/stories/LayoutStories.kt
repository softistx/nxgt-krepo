package com.strange.material.demo.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.demo.storyGroup
import com.strange.material.display.Card
import com.strange.material.display.ListTile
import com.strange.material.layout.LoadMoreButton
import com.strange.material.layout.RefreshBox
import com.strange.material.layout.ResponsiveGrid
import com.strange.material.layout.ScrollToTop
import com.strange.material.text.Typography

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
