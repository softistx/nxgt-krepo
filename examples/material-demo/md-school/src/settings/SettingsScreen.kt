package com.softistx.material.school.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.school.common.ScreenFrame
import com.softistx.material.school.common.isWide
import com.softistx.material.school.session.SchoolSession

/** Account, theme, language, notifications, security, help and the showcase's own switches. */
@Composable
fun SettingsScreen(session: SchoolSession) {
    ScreenFrame(title = "Settings", session = session) {
        BoxWithConstraints {
            val gap = Arrangement.spacedBy(StxTheme.spacing.md)
            if (maxWidth.isWide()) {
                Row(horizontalArrangement = gap, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = gap) { LeftSections(session) }
                    Column(Modifier.weight(1f), verticalArrangement = gap) { RightSections(session) }
                }
            } else {
                Column(verticalArrangement = gap) {
                    LeftSections(session)
                    RightSections(session)
                }
            }
        }
    }
}

@Composable
private fun LeftSections(session: SchoolSession) {
    AccountSection(session)
    AppearanceSection(session.preferences)
    LanguageSection(session)
    SecuritySection(session)
}

@Composable
private fun RightSections(session: SchoolSession) {
    NotificationsSection(session.preferences)
    ShowcaseSection(session)
    HelpSection()
    ResetSection(session)
}
