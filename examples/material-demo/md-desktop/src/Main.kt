package com.softistx.material.demo.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.softistx.material.demo.MaterialDemo
import com.softistx.material.school.SchoolApp

/**
 * The desktop launcher: a window around [MaterialDemo], or around [SchoolApp] when the first
 * argument is `school` (`./kotlin run -m md-desktop -- school`). The initial size is wide enough
 * for the catalogue's three-pane layout and for the School app's rail, and either folds when the
 * window is dragged narrower.
 */
fun main(args: Array<String>) {
    val picked: Pair<String, @Composable () -> Unit> =
        if (args.firstOrNull() == "school") {
            "stx-material School" to { SchoolApp() }
        } else {
            "stx-material" to { MaterialDemo() }
        }
    val (title, content) = picked
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = title,
            state = rememberWindowState(size = DpSize(1360.dp, 900.dp)),
        ) {
            content()
        }
    }
}
