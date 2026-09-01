package com.strange.spring.integration.workflow

import com.strange.redis.Redis
import com.strange.workflow.Workflow
import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowWorker
import com.strange.workflow.redis.RedisWorkflowStore
import com.strange.workflow.store.WorkflowStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.toKotlinDuration

/**
 * One `stx-workflow` engine for the application, over the `WorkflowStore` bean it finds.
 *
 * ```yaml
 * stx:
 *   redis: { enabled: true, uri: redis://localhost:6379, namespace: orders }
 *   workflow:
 *     enabled: true
 *     worker: { enabled: true }
 * ```
 *
 * ```kotlin
 * @Bean fun checkout(stock: Stock, payments: Payments): Workflow<Checkout> =
 *     workflowOf(CheckoutWorkflow(stock, payments))
 * ```
 *
 * **Every `Workflow<*>` bean is registered.** That is the whole wiring, and it is the one thing this
 * had to get right: an instance is stored under its workflow's *name*, so an engine that cannot look
 * that name up cannot resume it after a restart — and a process that registered half the fleet's
 * workflows will fail on the other half. Collecting them as beans means a workflow is registered by
 * existing, rather than by also being remembered in a list somewhere.
 *
 * The store is not built here unless it can be for free: with `stx-workflow-db` on the classpath
 * and a `Redis` bean present, one is made over that same connection. Anything else — a store of
 * your own, a second Redis, a Mongo store when there is one — is a `WorkflowStore` bean, and
 * `@ConditionalOnMissingBean` steps aside for it.
 */
@AutoConfiguration
@EnableConfigurationProperties(
    WorkflowIntegrationProperties::class,
    WorkflowWorkerProperties::class,
    WorkflowRedisProperties::class,
)
@ConditionalOnClass(WorkflowEngine::class)
@ConditionalOnProperty(prefix = "stx.workflow", name = ["enabled"], havingValue = "true")
class WorkflowIntegrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(WorkflowStore::class)
    fun stxWorkflowEngine(
        store: WorkflowStore,
        workflows: ObjectProvider<Workflow<*>>,
    ): WorkflowEngine = WorkflowEngine(store) { workflows.orderedStream().forEach(::register) }

    /**
     * The worker, and the scope it runs on, as one `SmartLifecycle`.
     *
     * A bean that were only `AutoCloseable` would be closed at shutdown and never started; a bean
     * that started itself in an `@PostConstruct` would start before the rest of the context is
     * ready, which for a worker means resuming instances against half-built collaborators. The
     * lifecycle is what puts the start after the context is refreshed and the stop before it is
     * torn down.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(WorkflowEngine::class)
    @ConditionalOnProperty(prefix = "stx.workflow.worker", name = ["enabled"], havingValue = "true")
    fun stxWorkflowWorker(
        engine: WorkflowEngine,
        properties: WorkflowWorkerProperties,
    ): WorkflowWorkerLifecycle =
        WorkflowWorkerLifecycle(
            WorkflowWorker(
                engine = engine,
                poll = properties.poll?.toKotlinDuration() ?: WORKER_POLL,
                batch = properties.batch,
                concurrency = properties.concurrency,
            ),
        )

    /**
     * A store over the connection `stx.redis` already opened.
     *
     * Nested and `@ConditionalOnClass` so the enclosing configuration can be read without
     * `stx-workflow-db` on the classpath: a method signature naming a missing class is a
     * `NoClassDefFoundError` at context refresh, and the condition on the outer class is evaluated
     * too late to prevent it.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisWorkflowStore::class)
    class RedisStore {
        @Bean
        @ConditionalOnMissingBean(WorkflowStore::class)
        @ConditionalOnBean(Redis::class)
        fun stxWorkflowStore(
            redis: Redis,
            properties: WorkflowRedisProperties,
        ): WorkflowStore =
            RedisWorkflowStore(
                redis = redis,
                lease = properties.lease?.toKotlinDuration() ?: LEASE,
                retention = properties.retention?.toKotlinDuration() ?: RETENTION,
            )
    }
}
