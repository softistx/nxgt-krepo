package com.strange.workflow.spring

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * What `stx.workflow` runs with.
 *
 * There is no `uri`. A store is built over a connection somebody already opened — the `Redis`, the
 * `Jpa` or the `MongoDatabase` bean the matching `stx.*` group made — because a second pool for the
 * same server is one nobody asked for.
 *
 * The two groups here are separate classes rather than nested ones, which is the shape
 * `stx.data.mongo` and its two sub-groups already use: Spring's binder handles either, but the
 * metadata this module maintains by hand is keyed on a prefix, and a nested object binds as one key
 * called `worker` rather than as the four an IDE should complete.
 */
@ConfigurationProperties(prefix = "stx.workflow")
data class WorkflowIntegrationProperties(
    /** Builds the engine over the `WorkflowStore` bean, registering every `Workflow` bean with it. */
    val enabled: Boolean = false,
    /**
     * Which store to build, and **null means build none** — the application declares its own bean.
     *
     * There is no inference here on purpose. With one store this key would have been unnecessary;
     * with three, an application that has both a `Redis` and a `Jpa` bean is not telling anybody
     * where its workflow instances belong, and a library that guessed would put them somewhere
     * plausible and wrong. Naming it is one line, and it is the line that says what an operator
     * needs to know.
     */
    val store: WorkflowStoreKind? = null,
    /**
     * How long an instance lock is good for before the instance is assumed abandoned.
     *
     * It is not a deadline on a step: the lock renews while the work runs. It is how long after a
     * process dies before somebody else may pick up what it was doing.
     */
    val lease: Duration? = null,
    /**
     * How long a finished instance is kept before it is expired.
     *
     * A completed run is the audit trail somebody will want afterwards. A `Failed` one is exempt
     * whatever this says — it is waiting for a person, and expiring it would delete the only
     * description of what needs fixing.
     *
     * [WorkflowStoreKind.JPA] ignores it: a table has no TTL, so retention there is
     * `JpaWorkflowStore.purge` on a schedule the application owns.
     */
    val retention: Duration? = null,
    /**
     * How often a parent parked on a `child` node looks at the child again.
     *
     * A safety net rather than the mechanism: a child resumes its parent the moment it finishes, and
     * this only covers a process that died between those two writes. It needs the worker, or an
     * application scheduler calling `resume` — nothing polls on its own.
     */
    val childPoll: Duration? = null,
)

/** Where `stx.workflow` puts instances. One per store in `stx-workflow-db`. */
enum class WorkflowStoreKind {
    /** Over the `Redis` bean `stx.redis` opened. */
    REDIS,

    /** Over the `Jpa` bean `stx.jpa` opened — which must scan `com.strange.workflow.jpa` for its entity. */
    JPA,

    /** Over the `MongoDatabase` bean `stx.mongo` opened. */
    MONGO,
}

/**
 * Whether this process picks up instances nobody is advancing.
 *
 * **Off by default, and that is a decision rather than caution.** Enabling `stx.workflow` gives an
 * application a way to *run* workflows; enlisting it in recovering every abandoned instance in the
 * fleet is a separate question, and one whose answer usually differs between the API pods and the
 * two boxes that are supposed to do the recovering.
 */
@ConfigurationProperties(prefix = "stx.workflow.worker")
data class WorkflowWorkerProperties(
    val enabled: Boolean = false,
    /**
     * How long the worker waits between two looks at an empty index.
     *
     * A `java.time.Duration` — `1s`, `PT1S` — because Spring's binder has never heard of
     * `kotlin.time.Duration`, and one written that way would bind only while nobody set it.
     */
    val poll: Duration? = null,
    /** How many due instances it takes at a time. */
    val batch: Int = 32,
    /** How many instances it advances at once. */
    val concurrency: Int = 8,
)
