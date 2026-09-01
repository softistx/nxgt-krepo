package com.softistx.material.demo.knobs

/**
 * A control the panel knows how to draw. A story never constructs one — it asks [Knobs] for a
 * value and the declaration falls out of that call, so a knob cannot exist without something
 * reading it.
 */
sealed interface Knob {
    val label: String

    data class Flag(
        override val label: String,
    ) : Knob

    data class Choice(
        override val label: String,
        val options: List<Any>,
        val render: (Any) -> String,
    ) : Knob

    data class Text(
        override val label: String,
    ) : Knob

    data class Number(
        override val label: String,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int,
    ) : Knob
}
