package com.softistx.material.foundation.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** "Reduce Motion" and "Increase Contrast" (darker system colours) from Settings > Accessibility. */
@Composable
actual fun systemAccessibility(): SystemAccessibility =
    remember {
        SystemAccessibility(
            reduceMotion = UIAccessibilityIsReduceMotionEnabled(),
            increaseContrast = UIAccessibilityDarkerSystemColorsEnabled(),
        )
    }
