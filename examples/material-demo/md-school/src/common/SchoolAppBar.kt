package com.softistx.material.school.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.IconBadge
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.button.MoreMenu
import com.softistx.material.components.feedback.ToasterState
import com.softistx.material.components.navigation.AppBar
import com.softistx.material.components.surface.ConfirmDialog
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.components.surface.Tooltip
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.data.Conversations
import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolSession

/** The bar every screen wears: its title, search, the unread count and the account menu. */
@Composable
fun SchoolAppBar(
    title: String,
    session: SchoolSession,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    AppBar(
        title = title,
        modifier = modifier,
        navigationIcon = if (onBack != null) StxIcons.ChevronLeft else null,
        navigationDescription = "Back",
        onNavigation = onBack,
        actions = {
            Tooltip(text = "Search and jump to a screen") {
                IconButton(
                    icon = StxIcons.Search,
                    description = "Search and jump",
                    onClick = { session.paletteOpen = true },
                )
            }
            IconBadge(
                icon = StxIcons.Inbox,
                description = "Unread messages",
                count = Conversations.all.sumOf { it.unread },
                onClick = { session.open(SchoolScreen.Messages) },
            )
            MoreMenu(
                items =
                    listOf(
                        MenuItem("Settings", { session.open(SchoolScreen.Settings) }, StxIcons.Menu),
                        MenuItem("Sign out", { confirmSignOut = true }, StxIcons.Close),
                    ),
                description = "Account",
            )
        },
    )
    ConfirmDialog(
        visible = confirmSignOut,
        title = "Sign out?",
        text = "You will need your school account to sign back in.",
        onConfirm = {
            confirmSignOut = false
            session.toaster.say("Signed out (demo only)", Tone.Info)
        },
        onDismiss = { confirmSignOut = false },
        confirm = "Sign out",
    )
}

/** A toast, named for what a screen means by it. */
fun ToasterState.say(
    text: String,
    tone: Tone = Tone.Success,
) = show(text, tone)
