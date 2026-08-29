package com.strange.material.helpers

import androidx.compose.material3.windowsizeclass.*
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.*

object Breakpoints {
    val sm = 640.dp

    val md = 768.dp

    val lg = 1024.dp

    val xl = 1280.dp
}

val Dp.sm
    get() = this >= Breakpoints.sm

val Dp.md
    get() = this >= Breakpoints.md

val Dp.lg
    get() = this >= Breakpoints.lg

val Dp.xl
    get() = this >= Breakpoints.xl

val WindowSizeClass.wCompact: Boolean
    get() = this.widthSizeClass == WindowWidthSizeClass.Compact

val WindowSizeClass.hCompact: Boolean
    get() = this.heightSizeClass == WindowHeightSizeClass.Compact

val WindowSizeClass.wMedium: Boolean
    get() = this.widthSizeClass == WindowWidthSizeClass.Medium

val WindowSizeClass.hMedium: Boolean
    get() = this.heightSizeClass == WindowHeightSizeClass.Medium

val WindowSizeClass.wExpanded: Boolean
    get() = this.widthSizeClass == WindowWidthSizeClass.Expanded

val WindowSizeClass.hExpanded: Boolean
    get() = this.heightSizeClass == WindowHeightSizeClass.Expanded

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
val LocalWindowSize =
    compositionLocalOf { WindowSizeClass.calculateFromSize(DpSize(640.dp, 1024.dp)) }
