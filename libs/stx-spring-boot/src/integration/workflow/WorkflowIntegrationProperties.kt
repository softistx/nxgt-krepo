package com.strange.spring.integration.workflow

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * What `stx.workflow` runs with.
 *
 * There is no `uri` and no `store` key. Where instances live is a `WorkflowStore` bean, because a
 * store is a connection somebody already opened — `RedisWorkflowStore(redis)` shares the one
 * `stx.redis` made — and a second pool for the same server is one nobody asked for.
 *
 * The three groups here are separate classes rather than nested ones, which is the shape
 * `stx.data.mongo` and its two sub-groups already use: Spring's binder handles either, but the
 * metadata this module maintains by hand is keyed on a prefix, and a nested object binds as one key
 * called `worker` rather than as the four an IDE should complete.
 */
@ConfigurationProperties(prefix = "stx.workflow")
data class WorkflowIntegrationProperties(
    /** Builds the engine over the `WorkflowStore` bean, registering every `Workflow` bean with it. */
    val enabled: Boolean = false,
)

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

/**
 * The store built when `stx-workflow-db` is on the classpath and a `Redis` bean exists.
 *
 * Ignored entirely when the application declares its own `WorkflowStore` — which is what a Mongo or
 * a Postgres store will be until one ships.
 */
@ConfigurationProperties(prefix = "stx.workflow.redis")
data class WorkflowRedisProperties(
    /**
     * How long an instance lock is good for before the instance is assumed abandoned.
     *
     * It is not a deadline on a step: the lock renews while the work runs. It is how long after a
     * process dies before somebody else may pick up what it was doing.
     */
    val lease: Duration? = null,
    /**
     * How long a finished instance is kept before Redis expires it.
     *
     * A completed run is the audit trail somebody will want afterwards. A `Failed` one is exempt
     * whatever this says — it is waiting for a person, and expiring it would delete the only
     * description of what needs fixing.
     */
    val retention: Duration? = null,
)
