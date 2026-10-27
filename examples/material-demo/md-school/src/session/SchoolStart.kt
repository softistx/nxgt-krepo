package com.softistx.material.school.session

import com.softistx.material.foundation.theme.ColorMode

/**
 * Where the app opens. Everything a headless render needs to put a screen on a given face without
 * clicking: the screen itself is a separate argument of `SchoolApp`.
 */
data class SchoolStart(
    val colorMode: ColorMode = ColorMode.System,
    val data: DataState = DataState.Loaded,
    val studentId: Int? = null,
    val conversationId: Int? = null,
    /** Off for a render that must not depend on the clock: the motion helpers settle at once. */
    val animated: Boolean = true,
)
