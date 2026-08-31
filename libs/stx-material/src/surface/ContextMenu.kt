package com.strange.material.surface

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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

/**
 * A menu on right-click (desktop) or long-press (touch).
 *
 * Foundation's context-menu area is internal, so this is [Menu] at the pointer — the same items,
 * the gesture the platform actually uses.
 */
@Composable
fun ContextMenu(
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
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
