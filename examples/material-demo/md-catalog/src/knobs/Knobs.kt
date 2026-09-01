package com.softistx.material.demo.knobs

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue

/**
 * The typed controls of one story.
 *
 * Declaration happens as a side effect of reading: `knobs.flag("Enabled", true)` returns the
 * current value and, the first time it runs, tells the panel that this story has a switch. That is
 * what keeps a story to one expression per knob instead of a declaration block plus a lookup.
 *
 * The default travels with the declaration, so the panel and the preview agree on the first frame
 * — a chip row with nothing selected would otherwise be the reader's first impression of every
 * story. Only [values] is snapshot state; the declarations sit in a plain map behind a [revision]
 * counter that ticks once per newly seen label, so registering during composition schedules
 * exactly one extra pass rather than a loop.
 */
@Stable
class Knobs {
    private val declarations = LinkedHashMap<String, Knob>()
    private val defaults = HashMap<String, Any>()
    private val values = mutableStateMapOf<String, Any>()
    private var revision by mutableIntStateOf(0)

    val controls: List<Knob>
        get() {
            revision
            return declarations.values.toList()
        }

    fun current(label: String): Any? = values[label] ?: defaults[label]

    fun set(
        label: String,
        value: Any,
    ) {
        values[label] = value
    }

    fun flag(
        label: String,
        default: Boolean = false,
    ): Boolean = read(label, default) { Knob.Flag(label) }

    fun text(
        label: String,
        default: String,
    ): String = read(label, default) { Knob.Text(label) }

    fun number(
        label: String,
        default: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
    ): Float = read(label, default) { Knob.Number(label, range, steps) }

    fun <T : Any> choice(
        label: String,
        options: List<T>,
        default: T = options.first(),
        render: (T) -> String = { it.toString() },
    ): T {
        @Suppress("UNCHECKED_CAST")
        return read(label, default) { Knob.Choice(label, options, render as (Any) -> String) }
    }

    private fun <T : Any> read(
        label: String,
        default: T,
        build: () -> Knob,
    ): T {
        if (declarations.put(label, declarations[label] ?: build()) == null) {
            defaults[label] = default
            revision++
        }
        @Suppress("UNCHECKED_CAST")
        return current(label) as? T ?: default
    }
}

/** Every entry of an enum as a choice knob, labelled by its constant name. */
inline fun <reified T : Enum<T>> Knobs.enumChoice(
    label: String,
    default: T = enumValues<T>().first(),
): T = choice(label, enumValues<T>().toList(), default) { it.name }
