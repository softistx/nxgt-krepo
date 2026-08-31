package com.strange.workflow

/**
 * Where an instance is.
 *
 * Two of these are not reachable yet. [Awaiting] and [Sleeping] are the states a workflow enters
 * when it stops for something that is not a failure — a person approving a refund, a timer — and
 * phase one has no verb that produces either. They are declared here rather than added later
 * because the engine's loop and the persisted record are written around the full set: adding
 * `await(Approval)` then means a new node type and a new `signal` call, not a migration of every
 * record already in the store and a second pass over every `when` that switches on this.
 *
 * [Failed] is the one that asks for a person. It does not mean "the workflow failed" — that is
 * [Compensated], and it is an ordinary outcome — it means "the workflow failed and undoing it
 * failed too", which no policy this library could invent would resolve.
 */
enum class WorkflowStatus {
    /** A node is running, or the next one is about to. */
    Running,

    /** Stopped until a named signal arrives. Not reachable in phase one. */
    Awaiting,

    /** Stopped until a point in time. Not reachable in phase one. */
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
