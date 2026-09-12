package com.softistx.migrations.ktor

import com.softistx.ktor.required
import com.softistx.migrations.MigrationRecord
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/**
 * The ledger as it stood when the application started, as [Migrations] read it.
 *
 * A snapshot and not a live read, deliberately. Once the gate has passed, the ledger only changes
 * when another process migrates — and a route that re-read it every request would be asking a
 * database a question whose answer this process cannot act on anyway.
 */
val Application.migrations: List<MigrationRecord> get() = required(LedgerKey, "Migrations")

/** The same list, from a route. */
val ApplicationCall.migrations: List<MigrationRecord> get() = application.migrations
