package com.strange.jpa

import com.strange.jpa.json.jpaJson
import com.strange.jpa.naming.Naming
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * What [Jpa] connects with, and the few Hibernate settings worth naming in Kotlin.
 *
 * Everything else Hibernate understands goes in [properties], which is applied last and can
 * therefore override any of it — this type is a shortcut for the settings a deployment actually
 * changes, not a wrapper around the hundreds it does not.
 */
data class JpaConfig(
    /**
     * `postgresql://host:port/database`, the way a reactive driver spells it.
     *
     * Not a JDBC URL: nothing here goes through JDBC, and a `jdbc:` prefix is the first thing to
     * suspect when a connection fails for no visible reason.
     */
    val uri: String = "postgresql://localhost:5432/postgres",
    /** Sent separately because the URI does not carry them, and must not be made to. */
    val username: String? = null,
    val password: String? = null,
    /** The default schema for unqualified table names. Left to the server's `search_path` when null. */
    val schema: String? = null,
    /**
     * What a column is called when the entity does not say. `created_by`, not `createdby`.
     *
     * Worth naming here rather than leaving to a default nobody reads: it renames every column that
     * was not named by hand, so it is a decision to make once, before there is a schema.
     */
    val naming: Naming = Naming.SNAKE_CASE,
    /** What Hibernate does to the schema at startup. [SchemaMode.NONE] outside tests. */
    val schemaMode: SchemaMode = SchemaMode.NONE,
    /**
     * How many connections the pool holds.
     *
     * A reactive pool is not sized like a blocking one: connections are held only for the length of
     * a statement rather than for the length of a request, so the number that saturates a database
     * is much smaller than the number of concurrent requests.
     */
    val poolSize: Int = 10,
    /**
     * How long to wait for a connection from the pool before giving up.
     *
     * Worth setting. The default waits a long time, and a request queued behind an exhausted pool is
     * a request that has already lost — failing it frees the caller to retry or degrade, and turns
     * a saturated database into a visible error rather than a rising latency graph.
     */
    val connectTimeout: Duration? = null,
    /** How long an idle connection is kept before the pool closes it. */
    val idleTimeout: Duration? = null,
    /**
     * How many prepared statements each connection caches.
     *
     * Off in the driver by default, and it is the cheapest performance setting here: a cached
     * statement skips the parse and plan on every execution after the first. The cost is memory per
     * connection and a plan chosen without seeing that execution's parameters.
     */
    val statementCacheSize: Int? = null,
    /**
     * How many inserts or updates Hibernate sends in one batch.
     *
     * Unset means one statement per row, which is what makes a bulk load slow. It applies to writes
     * a session flushes, not to `mutate("delete from …")`, which is one statement already.
     */
    val batchSize: Int? = null,
    /** Logs every statement. Useful once, expensive always. */
    val showSql: Boolean = false,
    /**
     * What a JSON column is written and read with.
     *
     * Only the opaque form goes through it — an attribute whose type is a `@Serializable` class, a
     * `Map` or a `List`. An `@Embeddable` marked `@JdbcTypeCode(SqlTypes.JSON)` is written by
     * Hibernate from its own mapping model and never sees this. See [jpaJson] for what the default
     * changes and why.
     */
    val json: Json = jpaJson,
    /** Anything else, applied last, overriding everything above. */
    val properties: Map<String, String> = emptyMap(),
) {
    internal fun settings(): Map<String, String> =
        buildMap {
            put("hibernate.connection.url", uri)
            username?.let { put("hibernate.connection.username", it) }
            password?.let { put("hibernate.connection.password", it) }
            schema?.let { put("hibernate.default_schema", it) }
            put("hibernate.hbm2ddl.auto", schemaMode.setting)
            put("hibernate.connection.pool_size", poolSize.toString())
            connectTimeout?.let { put("hibernate.vertx.pool.connect_timeout", it.inWholeMilliseconds.toString()) }
            idleTimeout?.let { put("hibernate.vertx.pool.idle_timeout", it.inWholeMilliseconds.toString()) }
            statementCacheSize?.let { put("hibernate.vertx.prepared_statement_cache.max_size", it.toString()) }
            batchSize?.let { put("hibernate.jdbc.batch_size", it.toString()) }
            if (showSql) {
                put("hibernate.show_sql", "true")
                put("hibernate.format_sql", "true")
            }
            putAll(properties)
        }
}

/**
 * What Hibernate is allowed to do to the schema when it starts.
 *
 * [NONE] is the default and the only safe answer for a deployment: a schema is migrated by
 * something that keeps a history, not by an ORM inferring one from the classes it happens to have
 * been given. The rest are for tests and for a scratch database.
 */
enum class SchemaMode(
    internal val setting: String,
) {
    NONE("none"),
    VALIDATE("validate"),
    UPDATE("update"),
    CREATE("create"),
    CREATE_DROP("create-drop"),
}
