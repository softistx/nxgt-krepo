package com.strange.material.navigation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.strange.material.icon.Icon
import com.strange.material.icon.StrangeIcons
import com.strange.material.text.Typography

/**
 * A search field that can expand into its results.
 *
 * Material 3's `SearchBar` — the query, the expansion, the docked sheet of results — with this
 * library's string-in / string-out shape in front of it, so it sits next to a `TextField`
 * without a `SearchBarState` to hoist. Pass [results] for the sheet; leave it empty and the bar
 * never expands, which is the filter-in-place case.
 */
@Composable
fun Search(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSearch: (String) -> Unit = onQueryChange,
    active: Boolean = false,
    onActiveChange: (Boolean) -> Unit = {},
    enabled: Boolean = true,
    style: Style = Style,
    trailing: @Composable (() -> Unit)? = null,
    results: @Composable ColumnScope.() -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                expanded = active,
                onExpandedChange = onActiveChange,
                enabled = enabled,
                placeholder = { Typography(text = placeholder) },
                leadingIcon = { Icon(icon = StrangeIcons.Search, description = null) },
                trailingIcon = trailing,
                interactionSource = interactionSource,
            )
        },
        expanded = active,
        onExpandedChange = onActiveChange,
        modifier = modifier.fillMaxWidth().styleable(styleState, navigationItemStyle, style),
        content = results,
    )
}
