package com.softistx.spring.data.mongo.migration

/**
 * One change to the database, run once and never again.
 *
 * ```kotlin
 * @MigrationUnit("backfills every order's currency")
 * class V3Currencies(private val template: ReactiveMongoTemplate) : Migration {
 *     override suspend fun migrate() {
 *         template.updateMulti(Query(), Update().set("currency", "EUR"), "orders").awaitSingle()
 *     }
 * }
 * ```
 *
 * **The class name is the version.** `V3Currencies` is order 3, and the name has to match
 * `<prefix><digits><name>` — see [MigrationRunner] for the grammar and what happens to a bean that
 * does not match it.
 *
 * **There is no `rollback`.** The interface this was extracted from declared one and nothing ever
 * called it, which is worse than not having it: it reads as a promise that a failed migration is
 * undone, and no code anywhere kept that promise. A migration that needs undoing is undone by the
 * next migration, which is a thing somebody reviewed.
 */
interface Migration {
    /** Applies the change. Throwing marks this migration `FAILED` and stops the ones after it. */
    suspend fun migrate()
}
