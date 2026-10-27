package com.softistx.material.components.button

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClip(text: String): ClipEntry = ClipEntry(StringSelection(text))
