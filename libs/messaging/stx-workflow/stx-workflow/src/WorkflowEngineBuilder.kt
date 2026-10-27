package com.softistx.workflow

import com.softistx.common.serialization.lenientJson
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class WorkflowEngineBuilder internal constructor() {
    internal val definitions = mutableMapOf<String, Workflow<*>>()

    /**
     * What a context is written and read with.
     *
     * The default is lenient about unknown keys, because a stored context is a copy and not the
     * record: one written by the previous deploy, carrying a field this version has dropped, must
     * still read, or a rolling deploy becomes a fleet half of which cannot resume the other half's
     * instances. Pass a strict `Json` when that skew should be loud instead.
     */
    var json: Json = lenientJson

    /**
     * How often a parent parked on a `child` node looks at the child again.
     *
     * It is a safety net, not the mechanism: a child resumes its parent the moment it finishes, so
     * this only covers the process that died between those two writes. Shortening it makes recovery
     * from that faster and costs one load per parked parent per interval; lengthening it costs
     * nothing until something goes wrong.
     *
     * It also needs somebody polling — a `WorkflowWorker`, or an application scheduler calling
     * `resume`. An engine with no poller relies entirely on the child's own wake-up.
     */
    var childPoll: Duration = 1.minutes

    fun register(workflow: Workflow<*>) {
        require(definitions.put(workflow.name, workflow) == null) { "two workflows are registered as '${workflow.name}'" }
    }
}
