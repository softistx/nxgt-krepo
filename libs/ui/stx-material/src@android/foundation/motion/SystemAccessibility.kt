package com.softistx.material.foundation.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Reduce motion is the animator duration scale at 0: the developer option and "Remove animations" both write it. */
@Composable
actual fun systemReducesMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { animationsOff(context) }
}

private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
