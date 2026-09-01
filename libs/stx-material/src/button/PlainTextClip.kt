package com.softistx.material.button

import androidx.compose.ui.platform.ClipEntry

/**
 * A [ClipEntry] that holds [text].
 *
 * [ClipEntry] is an `expect` class — Android wraps `ClipData`, desktop a `Transferable`, iOS a
 * pasteboard item — so there is no common constructor. This is the one door CopyButton uses.
 */
internal expect fun plainTextClip(text: String): ClipEntry
