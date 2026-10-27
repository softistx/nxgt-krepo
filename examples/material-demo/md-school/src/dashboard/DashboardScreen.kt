package com.softistx.material.school.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.feedback.LoadingMark
import com.softistx.material.components.layout.RefreshBox
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.common.ScreenFrame
import com.softistx.material.school.common.SkeletonCards
import com.softistx.material.school.common.StateHost
import com.softistx.material.school.common.say
import com.softistx.material.school.session.DataState
import com.softistx.material.school.session.SchoolSession
import com.softistx.material.school.session.of
import kotlinx.coroutines.delay

/** Stats, today's lessons, recent marks and announcements. Pull down to refresh. */
@Composable
fun DashboardScreen(session: SchoolSession) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(600)
            refreshing = false
            session.toaster.say("Dashboard is up to date")
        }
    }
    val state = session.data.of(DashboardData.of(session), DashboardData.Empty)
    ScreenFrame(title = "Dashboard", session = session, scroll = false) {
        RefreshBox(refreshing = refreshing, onRefresh = { refreshing = true }, modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
            ) {
                StateHost(
                    state = state,
                    onRetry = { session.data = DataState.Loaded },
                    loading = { DashboardLoading() },
                ) { DashboardContent(it, session) }
            }
        }
    }
}

@Composable
private fun DashboardLoading() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingMark() }
    SkeletonCards()
}
