package com.softistx.migrations.db.sql

/**
 * The one thing a hand-written statement cannot avoid knowing about a server: how a bind parameter is
 * spelled.
 *
 * Postgres wants `$1`, MySQL wants `?`, and nothing between this library and the driver rewrites one
 * into the other. It is why [SqlMigrationSession] takes no parameters at all — a placeholder in the
 * migration-facing API would be a portability hole in the one signature that is supposed to be
 * portable — and it is the entire dialect surface of this ledger. Dialect knowledge is most of what
 * Flyway earns, and it is unused here.
 *
 * **There is deliberately nothing about duplicate keys in this file.** The first version of it had an
 * `on conflict do nothing` against an `on duplicate key update`, and MySQL turned out not to be able
 * to answer the question at all: with `CLIENT_FOUND_ROWS` set, which the Vert.x client sets, an
 * insert that hit an existing row reports one affected row, exactly as a successful insert does.
 * `SqlMigrationLedger` asks the database instead — it inserts, and when the insert fails it looks to
 * see whether the row is there. That costs a read on a path that should never be taken, and it is
 * right on every server rather than on the two this enum could name.
 *
 * **DB2 is absent**, and this is where it is refused rather than discovered: it has no
 * `create table if not exists` either, so a ledger there is a different design and not a third entry.
 */
internal enum class SqlDialect(
    private val numbered: Boolean,
) {
    POSTGRES(numbered = true),
    MYSQL(numbered = false),
    ;

    /** The [index]-th bind parameter, counting from one. */
    fun parameter(index: Int): String = if (numbered) "\$$index" else "?"

    companion object {
        /**
         * Read off the connection URI, which the caller has already given `Jpa` and which names the
         * driver Hibernate itself picks by.
         *
         * Anything else throws here, at construction, rather than at the first statement — the
         * failure is a deployment pointed at a server this ledger has never been run against, and
         * finding that out during `create table` is finding it out too late.
         */
        fun of(uri: String): SqlDialect =
            when {
                uri.startsWith("postgres") -> {
                    POSTGRES
                }

                uri.startsWith("mysql") || uri.startsWith("mariadb") -> {
                    MYSQL
                }

                else -> {
                    throw IllegalArgumentException(
                        "stx-migrations has no ledger for '$uri': it is written and verified against " +
                            "PostgreSQL and MySQL, and DB2 has no `create table if not exists` for this " +
                            "ledger to build itself with",
                    )
                }
            }
    }
}
