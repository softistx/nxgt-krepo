package com.softistx.material.demo.stories.components.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.navigation.AppBar
import com.softistx.material.components.navigation.AppBarSize
import com.softistx.material.components.navigation.Search
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme

/** The Bar stories of the Navigation group. */
internal fun StoryGroupBuilder.barStories() {
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
}
