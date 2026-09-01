package com.strange.spring.integration.workflow

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.jpa.Jpa
import com.strange.redis.Redis
import com.strange.workflow.Workflow
import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowWorker
import com.strange.workflow.jpa.JpaWorkflowStore
import com.strange.workflow.mongo.MongoWorkflowStore
import com.strange.workflow.redis.RedisWorkflowStore
import com.strange.workflow.store.WorkflowStore
import kotlinx.coroutines.runBlocking
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
 *     store: redis
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
 * **`stx.workflow.store` says where instances live, and nothing is guessed.** Each value builds its
 * store over the connection the matching `stx.*` group already opened; leaving it unset means the
 * application declares a `WorkflowStore` bean of its own, and `@ConditionalOnMissingBean` steps
 * aside for that in every case.
 */
@AutoConfiguration
@EnableConfigurationProperties(
    WorkflowIntegrationProperties::class,
    WorkflowWorkerProperties::class,
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
     * One nested configuration per store, each `@ConditionalOnClass` and each named by
     * `stx.workflow.store`.
     *
     * Nested so the enclosing configuration can be read without `stx-workflow-db` on the classpath:
     * a method signature naming a missing class is a `NoClassDefFoundError` at context refresh, and
     * the condition on the outer class is evaluated too late to prevent it.
     *
     * None of them carries a `@ConditionalOnBean` on its connection. Asking for `store: mongo`
     * without a `MongoDatabase` bean is a mistake worth an error at startup, not a context that
     * comes up quietly without a store and fails on the first workflow.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisWorkflowStore::class)
    @ConditionalOnProperty(prefix = "stx.workflow", name = ["store"], havingValue = "redis")
    class RedisStore {
        @Bean
        @ConditionalOnMissingBean(WorkflowStore::class)
        fun stxWorkflowStore(
            redis: Redis,
            properties: WorkflowIntegrationProperties,
        ): WorkflowStore =
            RedisWorkflowStore(
                redis = redis,
                lease = properties.lease?.toKotlinDuration() ?: LEASE,
                retention = properties.retention?.toKotlinDuration() ?: RETENTION,
            )
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JpaWorkflowStore::class)
    @ConditionalOnProperty(prefix = "stx.workflow", name = ["store"], havingValue = "jpa")
    class JpaStore {
        /**
         * `stx.jpa.packages` has to include `com.strange.workflow.jpa`, or the session factory has
         * no `WorkflowInstanceRow` and every call here fails on an unmapped entity. There is nothing
         * this configuration can do about that: the factory is built before it, from a list only the
         * application has.
         */
        @Bean
        @ConditionalOnMissingBean(WorkflowStore::class)
        fun stxWorkflowStore(
            jpa: Jpa,
            properties: WorkflowIntegrationProperties,
        ): WorkflowStore = JpaWorkflowStore(jpa, lease = properties.lease?.toKotlinDuration() ?: LEASE)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MongoWorkflowStore::class)
    @ConditionalOnProperty(prefix = "stx.workflow", name = ["store"], havingValue = "mongo")
    class MongoStore {
        /**
         * `runBlocking` for the same reason `stxJpa` uses it: the store creates its two indexes
         * before it exists, that is a suspending call, and a `@Bean` method cannot suspend. It runs
         * once, at refresh, on the thread that is already blocked waiting for the context.
         */
        @Bean
        @ConditionalOnMissingBean(WorkflowStore::class)
        fun stxWorkflowStore(
            database: MongoDatabase,
            properties: WorkflowIntegrationProperties,
        ): WorkflowStore =
            runBlocking {
                MongoWorkflowStore(
                    database = database,
                    lease = properties.lease?.toKotlinDuration() ?: LEASE,
                    retention = properties.retention?.toKotlinDuration() ?: RETENTION,
                )
            }
    }
}
