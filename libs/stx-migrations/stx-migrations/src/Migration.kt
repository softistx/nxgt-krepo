package com.softistx.migrations

/**
 * One change to a store, and the version that says when it runs.
 *
 * ```kotlin
 * class V1Orders : SqlMigration {
 *     override val version = 1L
 *     override val description = "the orders table"
 *
 *     override suspend fun migrate(context: SqlMigrationSession) {
 *         context.execute("create table orders (id bigint primary key, total numeric(12, 2) not null)")
 *     }
 * }
 * ```
 *
 * **[version] is declared, not parsed from the class name.** The prior art in this repo read it out
 * of a `^V(\d+)([A-Za-z_]…)$` regex, which bought a naming convention and cost a configurable
 * prefix, an ambiguity about what `V102` means, a CGLIB `$$` proxy name to strip, and a class the
 * regex rejected that ran nothing and said nothing. `stx-workflow`'s `Nodes.order` had already
 * settled this the other way, and this is the same answer: the number is a property, so the compiler
 * is the one checking it is there.
 *
 * **[C] is what the migration is handed** — a `MongoDatabase`, a SQL session — and it is a type
 * parameter rather than a fixed type so that the version, the description, the ordering rule and the
 * runner are written once for every store. It is contravariant because a migration that can take any
 * `Any` can stand in for one that takes a `MongoDatabase`.
 *
 * **A migration must be safe to attempt twice.** Not because the runner will — an `APPLIED` record
 * is skipped — but because a process killed between the change and the ledger write leaves the
 * change made and the record saying `RUNNING`, and the operator clearing that is choosing between
 * running it again and editing the ledger by hand. `create table if not exists`, an `updateMany`
 * filtered on the documents that still need it, `add column if not exists` — each of them makes that
 * choice easy.
 *
 * **There is no `rollback`.** Carried from the runner this replaces, whose KDoc had already given
 * the reason: a declared-but-uncalled one *"reads as a promise that a failed migration is undone, and
 * no code anywhere kept that promise."* A change that has to be undone is undone by the next
 * migration, which is a thing the ledger records and a person reviewed.
 *
 * **There is no checksum.** Flyway hashes a file because a file can be edited in place after it ran.
 * A migration here is a class in the repository, so the diff is the review and `git log` is the
 * history — a hash would only be able to tell you what a `git blame` already tells you, later and
 * from a database.
 */
interface Migration<in C> {
    /**
     * Migrations run lowest first, and two claiming one version abort the whole run before anything
     * is applied.
     *
     * A `Long` rather than an `Int`, because the other convention for this number is a timestamp —
     * `20260901120000` — and that does not fit in an `Int`. Sequential integers and timestamps are
     * both fine; what matters is that the number never changes once it has been applied anywhere.
     */
    val version: Long

    /** Recorded beside it, for whoever reads the ledger later. Defaults to the class's own name. */
    val description: String get() = this::class.java.simpleName

    /**
     * Applies the change.
     *
     * Throwing records this migration `FAILED`, leaves everything after it unrun, and stops the
     * application — that is what makes the runner a gate rather than a log line.
     */
    suspend fun migrate(context: C)
}

/**
 * Where a migration's [C] comes from, and what happens around it.
 *
 * A `fun interface` and not a plain `C`, because the interesting contexts are scoped: the SQL one
 * borrows a connection from the pool and gives it back, and a caller that wanted a transaction
 * around each migration would express it here and nowhere else. A store whose context is just a
 * handle writes `MigrationContext { it(database) }`.
 *
 * [use] answers nothing on purpose. A generic `<R>` would read as more general and would in fact be
 * less: Kotlin has no generic lambdas, so a `fun interface` whose method declares its own type
 * parameter cannot be written as one — and the only caller is the runner, which wants `Unit`.
 */
fun interface MigrationContext<out C> {
    suspend fun use(block: suspend (C) -> Unit)
}
