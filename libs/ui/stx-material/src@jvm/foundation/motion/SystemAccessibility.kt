package com.softistx.material.foundation.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Desktop follows **only** `-Dstx.material.reduceMotion=true` (or `false`); with the property absent the
 * answer is "not reduced", and no process is spawned. The JVM has no hook for the OS setting
 * (`NSWorkspace` needs JNA, which this library does not take on), so an app that wants the OS's answer
 * asks for it with [readDesktopReducedMotion] and passes it in through `motion`.
 * The property is read once per composition of the theme, not on every recomposition.
 */
@Composable
actual fun systemReducesMotion(): Boolean = remember { DesktopPreferences.override(REDUCE_MOTION_PROPERTY) ?: false }
