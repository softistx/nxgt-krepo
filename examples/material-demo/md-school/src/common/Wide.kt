package com.softistx.material.school.common

import androidx.compose.ui.unit.Dp
import com.softistx.material.components.layout.ListDetailLayoutDefaults

/** True when [this] width has room for two panes. Screens that lay out columns ask the same question. */
fun Dp.isWide(): Boolean = this >= ListDetailLayoutDefaults.WideThreshold
