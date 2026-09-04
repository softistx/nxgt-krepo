package com.softistx.r2jdbc

import io.vertx.core.Vertx
import io.vertx.mysqlclient.MySQLBuilder
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlConnectOptions
import java.util.concurrent.TimeUnit

/**
 * Which server is at the other end, and the three things this library has to know about it.
 *
 * Everything database-specific in `stx-r2jdbc` is here, and it is a short list on purpose:
 *
 * 1. **which driver builds the pool** — `PgBuilder` or `MySQLBuilder`, chosen from the URI scheme
 *    the same way Hibernate Reactive picks one, so a deployment changes a string and nothing else;
 * 2. **how a bind parameter is spelled** — see [numbered], and `Placeholders` for what is done
 *    about it;
 * 3. **how a connection is put in a schema** — see [useSchema].
 *
 * Nothing else diverges, and that is a measurement rather than a hope: `DriverContractTest` asks
 * both servers the same eleven questions, and the only answers that differ are the ones named here
 * plus two the row mapper has to absorb — Postgres folds an unquoted column alias to lower case
 * where MySQL keeps it, and a `boolean` reads back as a `Boolean` on Postgres and a `Byte` on MySQL.
 *
 * **DB2 is absent, and this is where it is refused rather than discovered.** `stx-jpa` names its
 * driver because Hibernate speaks DB2 for free; here every server costs a placeholder rule, a
 * schema statement and a pair of specs that prove both, and nothing has asked for it.
 */
internal enum class Backend(
    /** Whether the server counts its parameters — `$1` on Postgres, an anonymous `?` on MySQL. */
    val numbered: Boolean,
) {
    POSTGRES(numbered = true),
    MYSQL(numbered = false),
    ;

    /** The statement that puts a freshly opened connection in [schema]. */
    fun useSchema(schema: String): String =
        when (this) {
            // `set search_path` and not `set schema`: the second one exists but takes a single
            // schema and drops `pg_catalog` from the path, which breaks unqualified `now()`.
            POSTGRES -> "set search_path to \"$schema\""

            // In MySQL a schema *is* a database, so this is the same statement the URI's path
            // segment already performs — and it is issued anyway, because a caller who names both
            // means the one they named here.
            MYSQL -> "use `$schema`"
        }

    /** A pool onto [config], with [R2jdbcConfig.schema] applied to every connection it opens. */
    fun pool(
        vertx: Vertx,
        config: R2jdbcConfig,
    ): Pool {
        val builder =
            when (this) {
                POSTGRES -> PgBuilder.pool()
                MYSQL -> MySQLBuilder.pool()
            }
        return builder
            .connectingTo(connectOptions(config))
            .with(poolOptions(config))
            // The schema is set as the connection opens rather than before each statement: the pool
            // reuses connections, so this is paid once per connection instead of once per query, and
            // a `transaction { }` cannot start on a connection that has not had it.
            //
            // `close()` here means *hand it back to the pool*, which is the whole protocol of a
            // connect handler — and it is called on the failure path too, because there is no way
            // to reject a connection from inside one. That is why `R2jdbc.connect` proves the schema
            // separately: a `set search_path` that fails here would otherwise reach the caller as
            // `relation "…" does not exist` on their first query.
            .withConnectHandler { connection ->
                when (val schema = config.schema) {
                    null -> {
                        connection.close()
                    }

                    else -> {
                        connection
                            .query(useSchema(schema))
                            .execute()
                            .onComplete { connection.close() }
                    }
                }
            }.using(vertx)
            .build()
    }

    private fun poolOptions(config: R2jdbcConfig): PoolOptions =
        PoolOptions().setMaxSize(config.poolSize).apply {
            config.connectTimeout?.let {
                connectionTimeout = it.inWholeMilliseconds.toInt()
                connectionTimeoutUnit = TimeUnit.MILLISECONDS
            }
            config.idleTimeout?.let {
                idleTimeout = it.inWholeMilliseconds.toInt()
                idleTimeoutUnit = TimeUnit.MILLISECONDS
            }
        }

    private fun connectOptions(config: R2jdbcConfig): SqlConnectOptions =
        when (this) {
            POSTGRES -> PgConnectOptions.fromUri(config.uri)
            MYSQL -> MySQLConnectOptions.fromUri(config.uri)
        }.apply {
            config.username?.let { user = it }
            config.password?.let { password = it }
            config.statementCacheSize?.let {
                cachePreparedStatements = it > 0
                preparedStatementCacheMaxSize = it
            }
        }

    companion object {
        /**
         * Read off the connection URI, and refused loudly for anything else.
         *
         * A `jdbc:` prefix is named separately because it is the mistake this repo's URIs invite —
         * every other library here takes the reactive spelling, and the two differ by five
         * characters that a copy from a Spring properties file carries along.
         */
        fun of(uri: String): Backend =
            when {
                uri.startsWith("postgres") -> {
                    POSTGRES
                }

                uri.startsWith("mysql") || uri.startsWith("mariadb") -> {
                    MYSQL
                }

                uri.startsWith("jdbc:") -> {
                    throw R2jdbcException(
                        "'$uri' is a JDBC URL, and nothing in this repository speaks JDBC. Drop the " +
                            "`jdbc:` prefix — the reactive spelling is `postgresql://host:port/database`",
                    )
                }

                else -> {
                    throw R2jdbcException(
                        "stx-r2jdbc has no backend for '$uri': it is written and measured against " +
                            "PostgreSQL and MySQL, and each further server costs a placeholder rule and " +
                            "the specs that prove it",
                    )
                }
            }
    }
}
