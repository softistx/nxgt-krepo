package com.strange.spring.integration.jpa

import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import com.strange.jpa.SchemaMode
import com.strange.jpa.naming.Naming
import kotlinx.coroutines.runBlocking
import org.hibernate.reactive.stage.Stage
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import java.time.Duration
import kotlin.time.toKotlinDuration

/**
 * What `stx.jpa` connects with.
 *
 * A subset of `JpaConfig` — the settings a deployment changes — plus [properties], which is applied
 * last and can name anything Hibernate understands. Widening this class every time somebody needs a
 * Hibernate setting is how a config type becomes a worse copy of the thing it configures.
 */
@ConfigurationProperties(prefix = "stx.jpa")
data class JpaIntegrationProperties(
    /** Builds the session factory at startup. Off unless asked for. */
    val enabled: Boolean = false,
    /**
     * `postgresql://host:port/database`, the way a reactive driver spells it.
     *
     * **Not a JDBC URL.** Nothing here goes through JDBC, and a `jdbc:` prefix is the first thing to
     * suspect when a connection fails for no visible reason.
     */
    val uri: String = "postgresql://localhost:5432/postgres",
    /** Sent separately because the URI does not carry them, and must not be made to. */
    val username: String? = null,
    val password: String? = null,
    /** The default schema for unqualified table names. Left to the server's `search_path` when null. */
    val schema: String? = null,
    /**
     * The packages entities are scanned from.
     *
     * Required when enabled: `Jpa.connect` needs its entity classes named, and a `module.yaml` is
     * not a place a class list can come from. `Jpa.scan` also finds `@Converter`s, which
     * `addAnnotatedClass` does not — so an application mapping its own types wants this rather than
     * a hand-written list.
     */
    val packages: List<String> = emptyList(),
    /** What a column is called when the entity does not say. `created_by`, not `createdby`. */
    val naming: Naming = Naming.SNAKE_CASE,
    /**
     * What Hibernate does to the schema at startup.
     *
     * `NONE` outside tests: a schema is migrated by something that keeps a history, not by an ORM
     * inferring one from the classes it happens to have been handed.
     */
    val schemaMode: SchemaMode = SchemaMode.NONE,
    /**
     * How many connections the pool holds.
     *
     * A reactive pool is not sized like a blocking one — a connection is held for the length of a
     * statement, not of a request — so the number that saturates a database is much smaller than
     * the number of concurrent requests.
     */
    val poolSize: Int = 10,
    /**
     * How long to wait for a connection before failing, rather than queueing behind an exhausted pool.
     *
     * A `java.time.Duration` and not a `kotlin.time.Duration`: Spring's binder knows the first one —
     * `30s`, `PT30S` — and has never heard of the second, so a `kotlin.time.Duration` here would bind
     * only when nobody set it. [config] converts.
     */
    val connectTimeout: Duration? = null,
    /** How long an idle connection is kept before the pool closes it. Same type, same reason. */
    val idleTimeout: Duration? = null,
    /** How many prepared statements each connection caches. Off in the driver by default. */
    val statementCacheSize: Int? = null,
    /** How many inserts or updates Hibernate sends in one batch. Unset is one statement per row. */
    val batchSize: Int? = null,
    /** Logs every statement. Useful once, expensive always. */
    val showSql: Boolean = false,
    /** Anything else Hibernate understands, applied last and overriding everything above. */
    val properties: Map<String, String> = emptyMap(),
) {
    internal fun config(): JpaConfig =
        JpaConfig(
            uri = uri,
            username = username,
            password = password,
            schema = schema,
            naming = naming,
            schemaMode = schemaMode,
            poolSize = poolSize,
            connectTimeout = connectTimeout?.toKotlinDuration(),
            idleTimeout = idleTimeout?.toKotlinDuration(),
            statementCacheSize = statementCacheSize,
            batchSize = batchSize,
            showSql = showSql,
            properties = properties,
        )
}

/**
 * A Hibernate Reactive session factory over `stx-jpa`, built from the entities in `stx.jpa.packages`.
 *
 * ```yaml
 * stx:
 *   jpa:
 *     enabled: true
 *     uri: "postgresql://localhost:5432/orders"
 *     username: app
 *     packages: [ com.acme.orders.domain ]
 * ```
 *
 * **`runBlocking` at bean creation, deliberately.** `Jpa.connect` suspends because reading the
 * annotations off every entity and standing up the service registry is ordinary blocking work that
 * belongs on `Dispatchers.IO`, and a `@Bean` method cannot suspend. The block is on the thread that
 * is starting the application, which is doing nothing else and is not an event loop — this is the
 * one place where blocking is the correct answer rather than a shortcut.
 *
 * On the default `schemaMode` nothing connects here: the pool opens its first connection when
 * something asks for a session, so a wrong password surfaces on first use rather than at startup.
 * Any other mode has schema work to do and therefore does connect, which is the point of choosing
 * one.
 *
 * The `Stage.SessionFactory` is published as a bean of its own, because that is what code written
 * against Hibernate Reactive rather than against this library asks for.
 */
@AutoConfiguration
@EnableConfigurationProperties(JpaIntegrationProperties::class)
@ConditionalOnClass(Jpa::class)
@ConditionalOnProperty(prefix = "stx.jpa", name = ["enabled"], havingValue = "true")
class JpaIntegrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun stxJpa(properties: JpaIntegrationProperties): Jpa {
        require(properties.packages.isNotEmpty()) {
            "stx.jpa.enabled is true but stx.jpa.packages is empty; there would be no entities to map"
        }
        return runBlocking { Jpa.scan(properties.config(), properties.packages) }
    }

    @Bean
    @ConditionalOnMissingBean
    fun stxSessionFactory(jpa: Jpa): Stage.SessionFactory = jpa.factory
}
