package com.strange.workflow

import com.strange.workflow.store.WorkflowRecord

/**
 * One run, as its caller sees it: the stored record, and its context decoded back into `C`.
 *
 * The record is the whole truth and is deliberately not hidden — the journal is what an operator
 * reads when something went wrong, and wrapping it in accessors would only mean adding one every
 * time somebody needs a field this class did not anticipate.
 */
class WorkflowInstance<C> internal constructor(
    val record: WorkflowRecord,
    /** The context as the instance left it: after the last node that ran, or as the unwind found it. */
    val context: C,
) {
    val id: String get() = record.id
    val status: WorkflowStatus get() = record.status
    val error: WorkflowError? get() = record.error

    /** True when every node succeeded. */
    val isOk: Boolean get() = status == WorkflowStatus.Completed
}
