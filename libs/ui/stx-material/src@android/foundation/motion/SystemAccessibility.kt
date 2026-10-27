package com.softistx.material.foundation.motion

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Reduce motion is the animator duration scale at 0 — the developer option and the "Remove
 * animations" accessibility switch both write it. Increase contrast is the contrast level of
 * Android 14 (`UiModeManager.getContrast`), and the high-contrast text switch below it.
 */
@Composable
actual fun systemAccessibility(): SystemAccessibility {
    val context = LocalContext.current
    return remember(context) { SystemAccessibility(reduceMotion = animationsOff(context), increaseContrast = contrastRaised(context)) }
}

private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun contrastRaised(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).contrast > 0f
    } else {
        Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
    }
