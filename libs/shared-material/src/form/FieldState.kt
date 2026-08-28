package com.strange.material.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * One input's whole state: what it holds, whether it is acceptable, and whether the reader has
 * earned the right to be told it is not.
 *
 * That last part is the reason this type exists rather than a `var text by remember`. A field is
 * invalid from the moment an empty form is drawn, and showing every error immediately is how a form
 * greets someone with six complaints about work they have not started. So an error is *held* until
 * the field is left ([touch]) or the form is submitted ([force]) — [showError] is the only thing a
 * component asks about.
 *
 * The rules are captured once, when the field is created. A rule that depends on another field
 * reads it at check time — `Rules.matching { password.value }` — rather than being rebuilt.
 */
@Stable
class FieldState<T> internal constructor(
    val name: String,
    private val initial: T,
    private val rule: Validation<T>,
) {
    /** What the input holds. Changed through [change], so touching cannot be forgotten. */
    var value: T by mutableStateOf(initial)
        private set

    /** Whether the reader has been in this field and left it. */
    var touched: Boolean by mutableStateOf(false)
        private set

    /** Whether a submit attempt has demanded that every complaint be shown. */
    var forced: Boolean by mutableStateOf(false)
        private set

    /** The reason the current value is unacceptable, whether or not it is being shown. */
    val error: String? get() = rule.check(value)

    /** Nothing to complain about. Independent of whether the reader has been told. */
    val isValid: Boolean get() = error == null

    /** Changed from what it was given. A form uses this to decide whether leaving costs anything. */
    val dirty: Boolean get() = value != initial

    /** There is a complaint *and* the reader has earned it. This is what a component reads. */
    val showError: Boolean get() = error != null && (touched || forced)

    /** The message to put under the control, or `null` while it is still being held back. */
    val visibleError: String? get() = error.takeIf { showError }

    /** The reader typed. */
    fun change(next: T) {
        value = next
    }

    /** The reader left the field — the moment an error becomes fair to show. */
    fun touch() {
        touched = true
    }

    /** Submit was pressed: every complaint comes out at once. */
    internal fun force() {
        forced = true
    }

    /** Back to the value it was born with, and quiet again. */
    fun reset() {
        value = initial
        touched = false
        forced = false
    }
}

/**
 * A field on its own, for a screen that has one input rather than a form.
 *
 * ```kotlin
 * val email = rememberField("", Rules.required(), Rules.email())
 * TextField(email, label = "Email")
 * ```
 */
@Composable
fun <T> rememberField(
    initial: T,
    vararg rules: Validation<T>,
    name: String = "",
): FieldState<T> = remember { FieldState(name, initial, rules.toList().all()) }
