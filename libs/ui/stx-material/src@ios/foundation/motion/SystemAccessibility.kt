package com.softistx.material.foundation.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** "Reduce Motion" from Settings > Accessibility. */
@Composable
actual fun systemReducesMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
