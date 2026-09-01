package com.softistx.material.demo.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.softistx.material.demo.MaterialDemo

/**
 * The desktop launcher: a window around [MaterialDemo]. The initial size is wide enough for the
 * three-pane layout, so the catalogue opens in the shape it was designed for — drag it narrower
 * and it folds.
 */
fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "stx-material",
            state = rememberWindowState(size = DpSize(1360.dp, 900.dp)),
        ) {
            MaterialDemo()
        }
    }
