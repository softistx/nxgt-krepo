package com.softistx.material.components.button

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClip(text: String): ClipEntry = ClipEntry.withPlainText(text)
