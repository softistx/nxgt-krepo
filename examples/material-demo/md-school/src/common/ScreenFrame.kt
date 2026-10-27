package com.softistx.material.school.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.session.SchoolSession

/**
 * Bar on top, content below. [scroll] is off for a screen whose content is a lazy list or two
 * panes that scroll themselves, because a scrolling column cannot hold a list of unbounded height.
 */
@Composable
fun ScreenFrame(
    title: String,
    session: SchoolSession,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SchoolAppBar(title = title, session = session, onBack = onBack)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = StxTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
            content = content,
        )
    }
}
