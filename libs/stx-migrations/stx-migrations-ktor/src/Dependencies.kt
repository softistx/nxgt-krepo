package com.softistx.migrations.ktor

import com.softistx.migrations.MigrationRecord
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Makes the ledger [Migrations] read injectable, without reading it a second time.
 *
 * ```kotlin
 * install(Migrations) { gate(SqlMigrations(application.jpa, migrations)) }
 * provideMigrations()
 *
 * class SchemaReport(private val ledger: List<MigrationRecord>)   // no ApplicationCall in sight
 * ```
 *
 * Or in one line: `install(Migrations) { …; injectable = true }`.
 *
 * There is nothing here for the container to close. A `List<MigrationRecord>` is a value, which is
 * the same reason `provideWorkflows` has nothing to close and the connection plugins do.
 */
fun Application.provideMigrations() {
    val ledger = migrations
    dependencies {
        provide<List<MigrationRecord>> { ledger }
    }
}
