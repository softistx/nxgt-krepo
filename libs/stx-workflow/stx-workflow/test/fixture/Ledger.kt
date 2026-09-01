package com.strange.workflow.fixture

import kotlinx.serialization.Serializable

/**
 * The context the engine specs thread through their workflows.
 *
 * Every field has a default, which is the rule a persisted context lives by: an instance written by
 * one version has to decode under the next one.
 */
@Serializable
data class Ledger(
    val reservationId: String? = null,
    val chargeId: String? = null,
    val booking: String? = null,
    val express: Boolean = false,
    val note: String = "",
    val approvedBy: String? = null,
    val waits: Int = 0,
)

/** The payload of the specs' approval signal — a signal carries a type, not a bare name. */
@Serializable
data class Approval(
    val by: String,
    val note: String = "",
)

/** What actually ran, in order. Lives outside the context because it is an observation, not state. */
class Calls {
    private val entries = mutableListOf<String>()

    fun record(name: String) {
        synchronized(entries) { entries += name }
    }

    fun all(): List<String> = synchronized(entries) { entries.toList() }

    fun count(name: String): Int = all().count { it == name }

    fun clear() = synchronized(entries) { entries.clear() }
}

/** A failure the retry policies in these specs treat as ordinary. */
class Wobble(
    message: String = "wobble",
) : RuntimeException(message)
