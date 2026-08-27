package com.strange.jpa

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
    /** Logs every statement. Useful once, expensive always. */
    val showSql: Boolean = false,
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
