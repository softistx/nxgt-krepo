package com.softistx.material.form.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.konform.validation.Validation
import io.konform.validation.messagesAtDataPath
import kotlin.reflect.KProperty1

/**
 * A whole form as one value, plus the complaints it is currently entitled to make.
 *
 * The form does not own a field per input. It owns **one [T]** — the request body, the GraphQL
 * input, whatever the screen is actually assembling — and every field is a property of it. That is
 * what makes submitting a form `send(form.value)` rather than a hand-written map, and it is what
 * lets validation be declared in one place instead of scattered across the controls:
 *
 * ```kotlin
 * class SignInForm : FormState<SignInInput>(
 *     SignInInput(username = "", password = ""),
 *     listOf(SignInInput::username, SignInInput::password),
 * ) {
 *     override val validate = Validation {
 *         SignInInput::username { required() }
 *         SignInInput::password { required(); minLength(8) }
 *     }
 * }
 * ```
 *
 * ### When a complaint is allowed out
 *
 * [errors] holds only the paths that have been [update]d. A form drawn for the first time is
 * therefore invalid and silent — which is the whole point, since a form that greets someone with
 * six complaints about work they have not started is a form they abandon. The submit button reads
 * [isValid], which checks everything without publishing anything, so it is correctly disabled from
 * the first frame while still saying nothing. [validateAll] is what finally makes every field
 * speak, and it belongs on the submit path and nowhere else.
 *
 * Messages are plain strings supplied where the rule is declared, so a caller with a string
 * catalogue passes translated text in — `required(strings.fieldRequired)` — and the library never
 * has to own a message catalogue of its own.
 */
@Stable
abstract class FormState<T>(
    private val initial: T,
    private val fields: List<KProperty1<T, *>>,
) {
    /** The form as one value. Changed through [update], so validation cannot be forgotten. */
    var value: T by mutableStateOf(initial)
        private set

    private val complaints = mutableStateMapOf<KProperty1<T, *>, String>()

    /** Every complaint currently worth showing, by the property it belongs to. */
    val errors: Map<KProperty1<T, *>, String> get() = complaints

    /** The rules, declared once over the whole of [T]. */
    abstract val validate: Validation<T>

    /**
     * Replace the form's value and re-check the one property that changed.
     *
     * The path is passed as well as the copy because it is what decides whose complaint may now
     * appear: touching `username` must not make `password` start shouting.
     */
    fun update(
        path: KProperty1<T, *>,
        block: (current: T) -> T,
    ) {
        value = block(value)
        recheck(listOf(path))
    }

    /** The message under [path], or `null` while it has nothing to say. */
    fun error(path: KProperty1<T, *>): String? = complaints[path]

    /** Whether [path] has a complaint that is currently being shown. */
    fun hasError(path: KProperty1<T, *>): Boolean = complaints.containsKey(path)

    /** Everything acceptable — checked in full, published to nobody. This is what a submit button reads. */
    fun isValid(): Boolean {
        val result = validate(value)
        return fields.all { result.errors.messagesAtDataPath(it).isEmpty() }
    }

    /** Any property has moved from what it was given — the "are you sure you want to leave" question. */
    fun isDirty(): Boolean = value != initial

    /**
     * Show every complaint at once and answer whether there are any.
     *
     * Calling it is what turns a quiet form into one that argues, so it belongs on the submit path.
     */
    fun validateAll(): Boolean {
        recheck(fields)
        return complaints.isEmpty()
    }

    /** [validateAll], then [block] with the form's value if there is nothing left to complain about. */
    fun submit(block: (T) -> Unit) {
        if (validateAll()) block(value)
    }

    /** Put every complaint away without changing a value — after a failed request has been re-tried. */
    fun clearErrors() {
        complaints.clear()
    }

    /** Back to the value it was born with, and quiet again. */
    fun reset() {
        value = initial
        complaints.clear()
    }

    private fun recheck(paths: List<KProperty1<T, *>>) {
        val result = validate(value)
        paths.forEach { path ->
            val message = result.errors.messagesAtDataPath(path).firstOrNull()
            if (message != null) complaints[path] = message else complaints.remove(path)
        }
    }
}

/**
 * A form that lives as long as the screen does.
 *
 * On a screen with a view model the form is built there instead and survives configuration change
 * with it; this is for the screens that have no view model to put it in.
 */
@Composable
fun <F : FormState<*>> rememberForm(factory: () -> F): F = remember { factory() }
