package com.softistx.migrations.db.sql

import com.softistx.migrations.Migration
import org.intellij.lang.annotations.Language

/**
 * A migration that is handed a [SqlMigrationSession].
 *
 * ```kotlin
 * class V1Orders : SqlMigration {
 *     override val version = 1L
 *     override val description = "the orders table"
 *
 *     override suspend fun migrate(context: SqlMigrationSession) {
 *         context.execute("create table if not exists orders (id bigint primary key)")
 *     }
 * }
 * ```
 *
 * An empty interface, for the reason [MongoMigration][com.softistx.migrations.db.mongo.MongoMigration]
 * gives: a generic `Migration<*>` erases, so nothing could tell the two apart at runtime.
 */
interface SqlMigration : Migration<SqlMigrationSession>

/**
 * What a SQL migration may say. Two verbs, and no `select`.
 *
 * **[execute] is sent as written and not prepared**, which is what makes DDL possible at all: it goes
 * out on the Vert.x simple-query protocol, so `create index`, two statements in one call and a
 * literal `?` used as a Postgres `jsonb` operator all reach the server unchanged. `NativeDdlTest` in
 * `stx-jpa` measures each of those, and measures the session verbs refusing them.
 *
 * **[update] is prepared**, which is the price of getting a row count back. One statement, no `?`
 * operator.
 *
 * **Neither takes parameters, and that is deliberate.** The two drivers do not agree on how a
 * placeholder is spelled — Postgres wants `$1`, MySQL wants `?` — so a parameter list in this
 * signature would be a portability hole in the one API that is supposed to be portable. A migration
 * is authored code rather than a request handler: the values it needs are literals it writes itself,
 * which is exactly what a `.sql` migration file is in every other migration tool.
 *
 * **There is no `select`.** A portable row type across the two transports would have to be
 * positional, and a read-modify-write loop inside a startup gate is the thing to avoid — a migration
 * says what it means in one statement: `update … where`, `insert … select`. An application that
 * genuinely has to read has its own `Jpa` in scope and can close over it.
 *
 * **An unqualified name lands in the connection's own schema**, not in `JpaConfig.schema`. Hibernate
 * applies that when it renders a statement from the mapping, and nothing renders these — the
 * `stx-jpa` README has the table, and this ledger qualifies its own two tables for exactly that
 * reason.
 */
interface SqlMigrationSession {
    /** One or more statements, sent as written. This is where DDL lives. */
    suspend fun execute(
        @Language("SQL") sql: String,
    )

    /** One `insert`, `update` or `delete`, and how many rows it touched. */
    suspend fun update(
        @Language("SQL") sql: String,
    ): Int
}
