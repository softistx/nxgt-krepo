package com.strange.material.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colour roles Material 3 does not have.
 *
 * M3 ships 48 roles and exactly one of them is semantic: `error`. A product needs four — a saved
 * confirmation, a neutral notice and a non-fatal caution are not the same thing as a failure, and
 * dressing all three in `error` or `tertiary` is how a palette stops meaning anything.
 *
 * This holds only the difference. Every other role is read from the M3 [ColorScheme] through
 * [scheme], because duplicating 48 values would give two places to disagree about `surface`.
 */
@Immutable
data class StrangeColors(
    val scheme: ColorScheme,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
) {
    /** The failure role, delegated: M3 already has it, and a second `error` would drift. */
    val error: Color get() = scheme.error
    val onError: Color get() = scheme.onError
    val errorContainer: Color get() = scheme.errorContainer
    val onErrorContainer: Color get() = scheme.onErrorContainer

    /** The four semantic roles as a set, for a component that switches on [Tone]. */
    fun tone(tone: Tone): ToneColors =
        when (tone) {
            Tone.Success -> ToneColors(success, onSuccess, successContainer, onSuccessContainer)
            Tone.Info -> ToneColors(info, onInfo, infoContainer, onInfoContainer)
            Tone.Warning -> ToneColors(warning, onWarning, warningContainer, onWarningContainer)
            Tone.Error -> ToneColors(error, onError, errorContainer, onErrorContainer)
        }
}

/** What a message or a status *means*, independent of how it is drawn. */
enum class Tone { Success, Info, Warning, Error }

/** One semantic role and the three colours that always travel with it. */
@Immutable
data class ToneColors(
    val main: Color,
    val onMain: Color,
    val container: Color,
    val onContainer: Color,
)
