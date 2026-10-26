package com.softistx.spring.data.mongo.audit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.javers.core.Javers
import org.javers.core.JaversBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * What `stx.data.mongo.audit` configures.
 *
 * Distinct from `stx.data.mongo.auditor`, which registers Spring Data's `ReactiveAuditorAware` so
 * `@CreatedBy` fills itself in. That one stamps *who* on the document; this one keeps the document's
 * whole history in a collection of its own.
 */
@ConfigurationProperties(prefix = "stx.data.mongo.audit")
data class AuditProperties(
    /**
     * Records every save and delete of an `@Auditable` document. Off unless asked for, like every
     * `stx.*` integration — an audit trail is a second copy of the data and a deployment's decision.
     */
    val enabled: Boolean = false,
    /**
     * The collection entries are written to.
     *
     * Handed to [AuditStore], which passes it to every call — see the note there on why this is not
     * the SpEL `@Document` name it would usually be. Changing it after entries exist starts a new,
     * empty history.
     */
    val collection: String = AuditEntry.COLLECTION,
)

/**
 * Registers the audit trail: a Javers instance, a scope to write on, the store, and the listener
 * that turns Spring Data's saves and deletes into entries.
 *
 * The listener takes the trail through an [ObjectProvider]: Spring Data collects its entity callbacks
 * while it creates the template, and the trail needs that template, so a direct reference is a cycle.
 */
@AutoConfiguration
@EnableConfigurationProperties(AuditProperties::class)
@ConditionalOnClass(Javers::class, ReactiveMongoTemplate::class)
@ConditionalOnProperty(prefix = "stx.data.mongo.audit", name = ["enabled"], havingValue = "true")
class AuditAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun javers(): Javers = JaversBuilder.javers().build()

    @Bean
    @ConditionalOnMissingBean
    fun auditStore(
        template: ReactiveMongoTemplate,
        properties: AuditProperties,
    ): AuditStore = AuditStore(template, properties.collection)

    @Bean
    @ConditionalOnMissingBean
    fun auditIndexes(store: AuditStore): AuditIndexes = AuditIndexes(store)

    @Bean
    @ConditionalOnMissingBean
    fun auditTrail(
        store: AuditStore,
        javers: Javers,
    ): AuditTrail = AuditTrail(store, javers)

    /**
     * The scope audit writes run on, cancelled when the context closes.
     *
     * A `SupervisorJob`, so one failed entry does not take the scope down and stop every later one.
     * `Dispatchers.IO` because the work is a database round trip and nothing else.
     *
     * Named `stxAuditScope` rather than typed-matched: an application is likely to have a
     * `CoroutineScope` bean of its own for its own work, and audit writes should not silently land
     * on it — or, worse, fail the context with two candidates.
     */
    @Bean(name = ["stxAuditScope"], destroyMethod = "close")
    @ConditionalOnMissingBean(name = ["stxAuditScope"])
    fun stxAuditScope(): AuditScope = AuditScope()

    @Bean
    @ConditionalOnMissingBean
    fun auditListener(
        trail: ObjectProvider<AuditTrail>,
        scope: AuditScope,
    ): AuditListener = AuditListener({ trail.getObject() }, scope.scope)
}

/**
 * A [CoroutineScope] with an off switch, so the context can close it.
 *
 * Spring cannot cancel a bare `CoroutineScope` — there is no method for it to call — so the scope is
 * wrapped in something `AutoCloseable`. Without this the audit writers outlive the application
 * context and are stopped only by the process exiting, which is how in-flight entries get lost.
 */
class AuditScope(
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("stx-audit")),
) : AutoCloseable {
    override fun close() {
        scope.cancel("the application context is closing")
    }
}

/** Creates the audit collection's indexes once the application is ready. A failure is logged, not fatal. */
class AuditIndexes(
    private val store: AuditStore,
) {
    private val log = LoggerFactory.getLogger(AuditIndexes::class.java)

    @EventListener(ApplicationReadyEvent::class)
    suspend fun create() {
        try {
            store.ensureIndexes()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn("stx.data.mongo.audit: could not create the audit indexes: {}", failure.message)
        }
    }
}
