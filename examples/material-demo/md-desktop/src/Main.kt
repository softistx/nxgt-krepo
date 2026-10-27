package com.softistx.material.demo.desktop

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
    val school = args.firstOrNull() == "school"
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = if (school) "stx-material School" else "stx-material",
            state = rememberWindowState(size = DpSize(1360.dp, 900.dp)),
        ) {
            if (school) SchoolApp() else MaterialDemo()
        }
    }
}
