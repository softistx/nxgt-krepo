package com.softistx.migrations.ktor

import com.softistx.ktor.publish
import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationRunner
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey
import kotlinx.coroutines.runBlocking

/**
 * Runs the migrations before the server binds, and does not let it bind if they fail.
 *
 * ```kotlin
 * install(JpaConnection) { config = JpaConfig(uri = …, username = …, password = …) }
 * install(Migrations) {
 *     gate(SqlMigrations(application.jpa, listOf(V1Orders(), V2OrderIndex())))
 * }
 *
 * get("/health/migrations") { call.respond(call.migrations.map { "${it.version} ${it.status}" }) }
 * ```
 *
 * **Install it after the connection plugin it reads from.** A runner is built from a `Jpa` or a
 * `MongoDatabase` that `install(JpaConnection)` or `install(MongoConnection)` put on the application,
 * and `application.jpa` throws by name when that install has not happened yet. Ktor runs install
 * blocks in order, so the ordering is the whole mechanism.
 *
 * **The blocking is the point.** `run()` suspends and plugin installation does not, so `runBlocking`
 * happens here — at startup, on the thread starting the application, before anything is serving,
 * which is the same argument `install(JpaConnection)` makes about building a session factory. An
 * exception leaves the install block, leaves `embeddedServer`, and the port is never opened. A
 * migration that failed must not be followed by a server answering requests against the schema it
 * failed to make.
 *
 * **It owns nothing and closes nothing.** A runner holds a ledger, a ledger holds a connection
 * somebody else opened, and none of the three is `AutoCloseable`. This publishes the ledger it read
 * so a health route can show it, and that is all it puts on the application.
 *
 * **The list of migrations is explicit.** There is no bean registry to ask here and no scan should
 * pretend to be one — `scanEntities`' KDoc already states the position: *"a list breaks the build when
 * a class moves; a scan finds nothing and starts perfectly, and the first query is where you learn
 * about it."* For migrations that second failure is the exact thing this library exists to prevent.
 */
val Migrations =
    createApplicationPlugin(name = "Migrations", createConfiguration = ::MigrationsConfiguration) {
        val ledger = runBlocking { pluginConfig.runners.flatMap { it.run() } }
        application.publish(LedgerKey, ledger)
        if (pluginConfig.injectable) application.provideMigrations()
    }

/** What [Migrations] runs. */
class MigrationsConfiguration {
    internal val runners = mutableListOf<MigrationRunner<*>>()

    /**
     * A runner to pass before the server starts.
     *
     * More than one is allowed and they run in the order they were added — an application migrating
     * both a SQL database and a MongoDB adds two. They are separate ledgers with separate locks, so
     * "in order" is a statement about this process and not a transaction across two servers.
     */
    fun gate(runner: MigrationRunner<*>) {
        runners += runner
    }

    /** [gate], for a call site that already has a list. */
    fun gate(runners: Iterable<MigrationRunner<*>>) {
        this.runners += runners
    }

    /**
     * Registers the ledger with Ktor's DI as well, so a class the container builds can take the
     * `List<MigrationRecord>` a health endpoint reports.
     *
     * Off by default, and it has to be: `ktor-server-di` is compile-only in this module, so an
     * application that never asks for this must not be made to carry it at runtime.
     */
    var injectable: Boolean = false
}

internal val LedgerKey = AttributeKey<List<MigrationRecord>>("com.softistx.migrations.MigrationLedger")
