package com.strange.material.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Several fields that submit together.
 *
 * It holds no copy of anything: the fields keep their own values and a form is the thing that can
 * ask all of them at once. That is what makes the submit button a one-liner —
 * `enabled = form.isValid` — with no aggregate state to keep in step.
 *
 * ```kotlin
 * val form = rememberForm()
 * val email = form.field("email", "", Rules.required(), Rules.email())
 * val terms = form.field("terms", false, Rules.checked())
 *
 * TextField(email, label = "Email")
 * Checkbox(terms, label = "I accept the terms")
 * Button("Create account", onClick = { form.submit { register(email.value) } })
 * ```
 */
@Stable
class FormState internal constructor() {
    private val fields = mutableStateMapOf<String, FieldState<*>>()

    /** Whether a submit has been attempted, which is when every field stops holding its errors. */
    var submitted: Boolean by mutableStateOf(false)
        private set

    /** Every field is acceptable. Reads each field's value, so it recomposes as they change. */
    val isValid: Boolean get() = fields.values.all { it.isValid }

    /** Any field has moved from what it was given — the "are you sure you want to leave" question. */
    val dirty: Boolean get() = fields.values.any { it.dirty }

    /** Every field's complaint that is currently worth showing, by field name. */
    val errors: Map<String, String> get() = fields.mapNotNull { (name, field) -> field.error?.let { name to it } }.toMap()

    /** Each field's current value, by name — what a request body is built from. */
    fun values(): Map<String, Any?> = fields.mapValues { (_, field) -> field.value }

    /**
     * Show every complaint and answer whether there are any.
     *
     * Calling it is what turns a quiet form into one that argues, so it belongs on the submit path
     * and nowhere else.
     */
    fun validate(): Boolean {
        submitted = true
        fields.values.forEach { it.force() }
        return isValid
    }

    /** [validate], then [block] if there is nothing left to complain about. */
    fun submit(block: () -> Unit) {
        if (validate()) block()
    }

    /** Every field back to its initial value, and the whole form quiet again. */
    fun reset() {
        submitted = false
        fields.values.forEach { it.reset() }
    }

    internal fun <T> register(
        name: String,
        build: () -> FieldState<T>,
    ): FieldState<T> {
        @Suppress("UNCHECKED_CAST")
        return fields.getOrPut(name) { build() } as FieldState<T>
    }
}

/** A form that lives as long as the screen does. */
@Composable
fun rememberForm(): FormState = remember { FormState() }

/**
 * Declares a field on this form and answers the same instance every recomposition.
 *
 * The name is the key: it is what [FormState.values] returns it under, and re-declaring the same
 * name answers the field that already exists rather than a second one.
 */
@Composable
fun <T> FormState.field(
    name: String,
    initial: T,
    vararg rules: Validation<T>,
): FieldState<T> = remember(name) { register(name) { FieldState(name, initial, rules.toList().all()) } }
