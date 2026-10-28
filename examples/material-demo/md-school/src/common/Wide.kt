package com.softistx.material.school.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val WideThreshold = 720.dp

/** True when [this] width has room for two panes. Screens that lay out columns ask the same question. */
fun Dp.isWide(): Boolean = this >= WideThreshold
