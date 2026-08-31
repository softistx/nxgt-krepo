package com.strange.material.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.strange.material.theme.Tone

/**
 * One destination in a [NavigationSuite].
 *
 * The suite picks bar, rail or drawer; this is only what to show. Decorations are optional and
 * **density-aware**: a compact bar keeps badge and unread dot (they fit the M3 item slot) and
 * drops supporting text, chip, shortcut and section headers, which belong on a rail or a drawer.
 *
 * A [badge] wins over [unread]: a count is more specific than a dot. [avatar] replaces the vector
 * with this library's [com.strange.material.media.Avatar], initials from [label]. [busy] is a
 * spinner over the leading, for a destination that is still catching up.
 */
@Immutable
data class NavigationDestination(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null,
    val supporting: String? = null,
    val badge: String? = null,
    val badgeTone: Tone = Tone.Info,
    val unread: Boolean = false,
    val chip: String? = null,
    val shortcut: String? = null,
    val section: String? = null,
    val avatar: Boolean = false,
    val picture: Any? = null,
    val busy: Boolean = false,
    val tone: Tone? = null,
    val enabled: Boolean = true,
)

internal sealed interface ResolvedBadge {
    data object None : ResolvedBadge

    data object Dot : ResolvedBadge

    data class Label(
        val text: String,
        val tone: Tone,
    ) : ResolvedBadge
}

internal fun NavigationDestination.resolvedBadge(): ResolvedBadge =
    when {
        badge != null -> ResolvedBadge.Label(badge, badgeTone)
        unread -> ResolvedBadge.Dot
        else -> ResolvedBadge.None
    }

internal fun NavigationDestination.showSupporting(compact: Boolean): Boolean = !compact && supporting != null

internal fun NavigationDestination.showChip(compact: Boolean): Boolean = !compact && chip != null

internal fun NavigationDestination.showShortcut(compact: Boolean): Boolean = !compact && shortcut != null

internal fun NavigationDestination.showSection(
    compact: Boolean,
    previous: String?,
): Boolean = !compact && section != null && section != previous

internal fun NavigationDestination.usesAvatar(): Boolean = avatar || picture != null
