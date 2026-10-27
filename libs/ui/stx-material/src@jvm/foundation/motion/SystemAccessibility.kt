package com.softistx.material.foundation.motion

import androidx.compose.runtime.Composable

/** The system properties that set a preference by hand, and win over whatever the OS says. */
internal const val REDUCE_MOTION_PROPERTY = "stx.material.reduceMotion"
internal const val INCREASE_CONTRAST_PROPERTY = "stx.material.increaseContrast"

/**
 * Desktop has no Compose hook for the OS settings, and `NSWorkspace.accessibilityDisplayShouldReduceMotion`
 * is not reachable from the JVM without JNA, which this library does not take on. So the OS is asked
 * the way a user would, through the command it ships (see [DesktopPreferences]), once per process;
 * `-Dstx.material.reduceMotion=true` and `-Dstx.material.increaseContrast=true` (or `false`) win.
 */
@Composable
actual fun systemAccessibility(): SystemAccessibility =
    SystemAccessibility(
        reduceMotion = DesktopPreferences.reduceMotion(),
        increaseContrast = DesktopPreferences.increaseContrast(),
    )
