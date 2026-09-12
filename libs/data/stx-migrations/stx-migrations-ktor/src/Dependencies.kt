package com.softistx.migrations.ktor

import com.softistx.migrations.MigrationRecord
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Registers the ledger [Migrations] read with Ktor's DI, without reading it a second time.
 *
 * ```kotlin
 * install(Migrations) { gate(SqlMigrations(application.jpa, migrations)) }
 *
 * class SchemaReport(private val ledger: List<MigrationRecord>)   // no ApplicationCall in sight
 * ```
 *
 * Called by [Migrations] at install — there is nothing to switch on.
 *
 * There is nothing here for the container to close. A `List<MigrationRecord>` is a value, which is
 * the same reason `provideWorkflows` has nothing to close and the connection plugins do.
 */
internal fun Application.provideMigrations() {
    val ledger = migrations
    dependencies {
        provide<List<MigrationRecord>> { ledger }
    }
}
