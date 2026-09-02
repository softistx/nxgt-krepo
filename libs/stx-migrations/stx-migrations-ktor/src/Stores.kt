package com.softistx.migrations.ktor

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.jpa.Jpa
import com.softistx.migrations.MigrationRunner
import com.softistx.migrations.db.mongo.MongoMigration
import com.softistx.migrations.db.mongo.MongoMigrations
import com.softistx.migrations.db.sql.SqlMigration
import com.softistx.migrations.db.sql.SqlMigrationSession
import com.softistx.migrations.db.sql.SqlMigrations
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The migrations to run against [jpa], declared where the plugin is installed.
 *
 * ```kotlin
 * install(JpaConnection) { config = JpaConfig(uri = …, username = …, password = …) }
 *
 * install(Migrations) {
 *     sql(application.jpa) {
 *         migration(V1Orders(), V2OrderIndex())
 *     }
 * }
 * ```
 *
 * Sugar over [gate][MigrationsConfiguration.gate] and nothing more — it builds exactly the
 * `SqlMigrations(jpa, listOf(…))` an application used to write itself and adds it to the same list.
 * Which means the two mix freely: an application with a ledger of its own keeps calling `gate`.
 *
 * **`stx.jpa.pool-size` must be at least two.** `SqlMigrations` refuses to be built otherwise, and
 * says why: a run holds one connection for the migration and needs a second for the lease watchdog.
 * The default is ten, so this only bites an application that turned it down.
 */
fun MigrationsConfiguration.sql(
    jpa: Jpa,
    block: SqlMigrationsBuilder.() -> Unit,
) {
    gate(SqlMigrationsBuilder().apply(block).runner(jpa))
}

/**
 * The migrations to run against [database], declared where the plugin is installed.
 *
 * ```kotlin
 * install(MongoDB) { config = MongoConfig(uri = …, database = "orders") }
 *
 * install(Migrations) {
 *     mongo(application.database) {
 *         migration(V1Seed(), V2Tags())
 *     }
 * }
 * ```
 *
 * `application.database` and not `application.mongo`: the latter is the client, and a ledger is
 * written in one database.
 *
 * An application migrating both stores calls this and [sql] in the same block. They are separate
 * ledgers with separate locks, so *in order* is a statement about this process and not a transaction
 * across two servers.
 */
fun MigrationsConfiguration.mongo(
    database: MongoDatabase,
    block: MongoMigrationsBuilder.() -> Unit,
) {
    gate(MongoMigrationsBuilder().apply(block).runner(database))
}

/**
 * What [sql] and [mongo] have in common: the migrations, and the four durations.
 *
 * The knobs are `stx.migrations.*` under different spelling — `lease`, `lock-timeout`, `lock-poll`
 * and `stale-after` — so an application that moves between the two stacks is tuning the same four
 * things. `docs/migrations.md` describes each of them once, for both.
 *
 * `sealed`, because the two subclasses below are the whole set: a third store is a third factory in
 * `stx-migrations-db` before it is anything here.
 */
sealed class MigrationsBuilder<M> {
    internal val collected = mutableListOf<M>()

    /**
     * Migrations to run, in any order — the runner sorts them by version and refuses two at one.
     *
     * Callable more than once, so a module can contribute its own without the install block having
     * to concatenate lists.
     */
    fun migration(vararg migrations: M) {
        collected += migrations
    }

    /** [migration], for a call site that already has a collection. */
    fun migration(migrations: Iterable<M>) {
        collected += migrations
    }

    /** How long the lock is good for before the process holding it is assumed gone. */
    var lease: Duration = 5.minutes

    /** How long to wait for another process to finish migrating before giving up and throwing. */
    var lockTimeout: Duration = 5.minutes

    /** How long to wait between attempts on the lock. */
    var lockPoll: Duration = 1.seconds

    /** How old a `RUNNING` record has to be before it is read as a process that died. */
    var staleAfter: Duration = 15.minutes
}

/** See [sql]. */
class SqlMigrationsBuilder internal constructor() : MigrationsBuilder<SqlMigration>() {
    /** The record table. The lock lives in `<table>_lock`. */
    var table: String = "stx_migrations"

    internal fun runner(jpa: Jpa): MigrationRunner<SqlMigrationSession> =
        SqlMigrations(
            jpa = jpa,
            migrations = collected,
            table = table,
            lease = lease,
            lockTimeout = lockTimeout,
            lockPoll = lockPoll,
            staleAfter = staleAfter,
        )
}

/** See [mongo]. */
class MongoMigrationsBuilder internal constructor() : MigrationsBuilder<MongoMigration>() {
    /** The record collection. The lock lives in `<collection>_lock`. */
    var collection: String = "stx_migrations"

    internal fun runner(database: MongoDatabase): MigrationRunner<MongoDatabase> =
        MongoMigrations(
            database = database,
            migrations = collected,
            collection = collection,
            lease = lease,
            lockTimeout = lockTimeout,
            lockPoll = lockPoll,
            staleAfter = staleAfter,
        )
}
