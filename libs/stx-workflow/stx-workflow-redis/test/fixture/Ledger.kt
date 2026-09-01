package com.strange.workflow.redis.fixture

import kotlinx.serialization.Serializable

/** The context these specs thread through their workflows. Every field has a default, as one must. */
@Serializable
data class Ledger(
    val reservationId: String? = null,
    val chargeId: String? = null,
    val note: String = "",
    val approvedBy: String? = null,
)

/** The payload of these specs' approval signal. */
@Serializable
data class Approval(
    val by: String,
)

/** What actually ran, in order. */
class Calls {
    private val entries = mutableListOf<String>()

    fun record(name: String) =
        synchronized(entries) {
            entries += name
            Unit
        }

    fun all(): List<String> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }
}
