package com.softistx.r2jdbc

import kotlin.time.Duration

/**
 * What [R2jdbc] connects with.
 *
 * Deliberately shaped like `JpaConfig`, minus everything that only means something to an ORM: there
 * is no schema mode, no naming strategy and no batch size here, because nothing in this library
 * generates a statement, exports a schema or decides what a column is called.
 */
data class R2jdbcConfig(
    /**
     * `postgresql://host:port/database` or `mysql://host:port/database`, the way a reactive driver
     * spells it — and the value [Backend] reads to decide which driver and which placeholder.
     *
     * Not a JDBC URL. A `jdbc:` prefix is refused at [R2jdbc.connect] rather than at the first
     * statement, because it is the mistake this repo's URIs invite and the failure it would
     * otherwise produce says nothing useful.
     */
    val uri: String = "postgresql://localhost:5432/postgres",
    /** Sent separately because the URI does not carry them, and must not be made to. */
    val username: String? = null,
    val password: String? = null,
    /**
     * The schema unqualified table names resolve in, set on every connection as it is opened.
     *
     * This is the half `stx-jpa` could not give a hand-written statement. `@SQLDelete` and
     * `nativeMutate` there take the SQL as written and send it as written, so a table name in one
     * is unqualified against whatever the server's default happens to be — which is why
     * `LegacyDollarNote` fails with *relation "legacy_notes" does not exist* against a configured
     * schema. Here the connection is put in the right schema before the caller's first statement,
     * so the caller never qualifies anything.
     *
     * Null leaves the server's own default alone.
     */
    val schema: String? = null,
    /**
     * How many connections the pool holds.
     *
     * A reactive pool is not sized like a blocking one: a connection is held for the length of a
     * statement rather than the length of a request, so the number that saturates a database is
     * much smaller than the number of concurrent requests. `transaction { }` is the exception —
     * it holds one for the length of the block — and a service whose transactions outnumber this
     * queues.
     */
    val poolSize: Int = 10,
    /**
     * How long to wait for a connection from the pool before giving up.
     *
     * Worth setting. A request queued behind an exhausted pool has already lost; failing it turns a
     * saturated database into a visible error rather than a rising latency graph.
     */
    val connectTimeout: Duration? = null,
    /** How long an idle connection is kept before the pool closes it. */
    val idleTimeout: Duration? = null,
    /**
     * How many prepared statements each connection caches.
     *
     * Off in the driver by default, and the cheapest performance setting here: a cached statement
     * skips the parse and the plan on every execution after the first. It costs memory per
     * connection, and a plan chosen without seeing that execution's parameters.
     */
    val statementCacheSize: Int? = null,
) {
    init {
        // The same rule `JpaConfig` states, for the same reason: a value that cannot work should
        // fail where it was written. `poolSize = 0` from a mis-read environment variable starts a
        // service cleanly and hangs it on the first statement, with no exception anywhere to find.
        require(poolSize >= 1) { "poolSize is $poolSize: a pool of none never hands out a connection" }
        statementCacheSize?.let { require(it >= 0) { "statementCacheSize is $it, which cannot be negative" } }
        connectTimeout?.let { require(it.isPositive()) { "connectTimeout is $it, which would give up before trying" } }
        idleTimeout?.let { require(it.isPositive()) { "idleTimeout is $it, which would close every connection at once" } }
        schema?.let {
            // Interpolated into `set search_path` / `use`, which take an identifier and not a
            // parameter — so the value is checked here rather than escaped there.
            require(it.matches(IDENTIFIER)) {
                "schema is '$it': it is written straight into `set search_path`, so it must be a plain " +
                    "identifier — letters, digits and underscores, not starting with a digit"
            }
        }
    }

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
