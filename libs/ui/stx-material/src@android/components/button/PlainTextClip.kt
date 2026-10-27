package com.softistx.material.components.button

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClip(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("plain text", text))
