package com.softistx.migrations.spring

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * What `stx.migrations` runs with.
 *
 * There is no `uri`. A ledger is built over a connection somebody already opened — the `Jpa` or the
 * `MongoDatabase` bean the matching `stx.*` group made, or one an application declared itself —
 * because a second pool for the same server is one nobody asked for. It is the same shape
 * `stx.workflow` uses, for the same reason.
 *
 * Every duration is a `java.time.Duration` — `5m`, `PT5M` — because Spring's binder has never heard
 * of `kotlin.time.Duration`, and one written that way would bind only while nobody set it.
 */
@ConfigurationProperties(prefix = "stx.migrations")
data class MigrationProperties(
    /** Runs the migrations before the context finishes refreshing, and refuses to start if they fail. */
    val enabled: Boolean = false,
    /**
     * Which ledger to build, and **null means build none** — the application declares its own
     * `MigrationRunner` bean.
     *
     * Nothing is inferred, on purpose. An application that has both a `Jpa` and a `MongoDatabase`
     * bean is not telling anybody which schema it means to migrate, and a library that guessed would
     * write a ledger somewhere plausible and wrong. An application migrating **both** names one here
     * and declares a `MigrationRunner` bean for the other: the gate runs every runner it finds.
     */
    val store: MigrationStoreKind? = null,
    /**
     * The table or collection the ledger lives in. The lock lives beside it, in `<name>_lock`.
     *
     * Worth changing only when something else already owns that name.
     */
    val name: String = "stx_migrations",
    /**
     * How long the migration lock is good for before the process holding it is assumed gone.
     *
     * Not a deadline on a migration: the lock renews at a third of this while the work runs, so it
     * expires only when the process holding it has stopped renewing it.
     */
    val lease: Duration? = null,
    /**
     * How long to wait for another instance to finish migrating before giving up and failing to
     * start.
     *
     * Generous on purpose, and this is where the migration lock deliberately differs from
     * `stx.workflow`'s: everyone waiting is waiting on the same one-off outcome, and an instance that
     * gave up and started serving would serve against a schema that does not exist yet.
     */
    val lockTimeout: Duration? = null,
    /** How long to wait between two attempts on the lock. */
    val lockPoll: Duration? = null,
    /**
     * How old a `RUNNING` record has to be before it is read as a process that died rather than one
     * that is slow.
     *
     * Only consulted while this instance holds the lock, so a live holder is never mistaken for a
     * dead one — it would still have the lock.
     */
    val staleAfter: Duration? = null,
)

/** Where `stx.migrations` keeps its ledger. One per store in `stx-migrations-db`. */
enum class MigrationStoreKind {
    /** Over the `MongoDatabase` bean — `stx.mongo`'s, or the one `stx.data.mongo` bridges from Spring Data. */
    MONGO,

    /** Over the `Jpa` bean `stx.jpa` opened. PostgreSQL and MySQL; DB2 is refused by name. */
    SQL,
}
