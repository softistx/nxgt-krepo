package com.softistx.workflow

import kotlinx.serialization.Serializable

/**
 * What went wrong, flattened into something that survives a round trip through the store.
 *
 * An exception cannot be persisted: its class may not exist in the process that reads the record
 * back, and its stack trace is about the JVM that threw it rather than about the workflow. What a
 * later reader needs is which node failed, what type the failure was and what it said — so that is
 * what is kept, and the live exception stays available as the `cause` of what the engine throws.
 */
@Serializable
data class WorkflowError(
    /** The qualified node name, as it appears in the journal. */
    val node: String,
    /** The exception's qualified class name. */
    val type: String,
    val message: String,
    /** How many attempts the node had before this became final. */
    val attempts: Int,
) {
    override fun toString(): String = "$node failed after $attempts attempt(s): $type: $message"

    internal companion object {
        fun of(
            node: String,
            cause: Throwable,
            attempts: Int,
        ) = WorkflowError(
            node = node,
            type = cause::class.qualifiedName ?: cause::class.toString(),
            message = cause.message ?: "",
            attempts = attempts,
        )
    }
}
