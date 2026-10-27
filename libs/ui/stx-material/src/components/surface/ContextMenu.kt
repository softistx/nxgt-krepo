package com.softistx.material.components.surface

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import com.softistx.material.foundation.style.styledBy

/**
 * A menu on right-click (desktop) or long-press (touch).
 *
 * Foundation's context-menu area is internal, so this is [Menu] at the pointer — the same items,
 * the gesture the platform actually uses. [style] dresses the area the menu opens on and has no
 * default look; the menu itself is [Menu]'s.
 */
@Composable
fun ContextMenu(
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
    style: Style = Style,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current

    fun openAt(
        x: Float,
        y: Float,
    ) {
        offset = with(density) { DpOffset(x.toDp(), y.toDp()) }
        expanded = true
    }

    Box(
        modifier =
            modifier
                .styledBy(style)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                val position = event.changes.first().position
                                openAt(position.x, position.y)
                            }
                        }
                    }
                }.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { openAt(it.x, it.y) })
                },
    ) {
        content()
        Menu(
            expanded = expanded,
            onDismiss = { expanded = false },
            items = items,
            offset = offset,
        )
    }
}
