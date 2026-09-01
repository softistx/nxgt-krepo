package com.softistx.workflow

/**
 * Where an instance is.
 *
 * [Awaiting] and [Sleeping] are the two that are not failures and not progress: the workflow
 * stopped on purpose, for a person or for a clock, and it will stop for as long as that takes. They
 * are the reason an instance is a record rather than a coroutine — a coroutine waiting two days for
 * an approval is a process that must not be redeployed for two days.
 *
 * [Failed] is the one that asks for a person. It does not mean "the workflow failed" — that is
 * [Compensated], and it is an ordinary outcome — it means "the workflow failed and undoing it
 * failed too", which no policy this library could invent would resolve.
 */
enum class WorkflowStatus {
    /** A node is running, or the next one is about to. */
    Running,

    /** Stopped until a named signal arrives — see `await`. */
    Awaiting,

    /** Stopped until a point in time — see `sleep`. */
    Sleeping,

    /** A node failed for good; the journal is being unwound. */
    Compensating,

    /** Every node succeeded. */
    Completed,

    /** A node failed, and every compensation owed for it succeeded. */
    Compensated,

    /** A node failed, and a compensation failed too. Nothing here will retry it. */
    Failed,

    /** Stopped on request. Whatever had already run was compensated first. */
    Cancelled,

    ;

    /** True once nothing will move this instance again without a person asking. */
    val isTerminal: Boolean
        get() = this == Completed || this == Compensated || this == Failed || this == Cancelled
}
