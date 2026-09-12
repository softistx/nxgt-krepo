package com.softistx.spring.testing

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query

/**
 * Empties [collections], so a scenario starts from a state it can name.
 *
 * The documents go and the collection stays, indexes included — `remove` and not `drop`, because
 * rebuilding an index per scenario costs more than the documents did. The context is shared across a
 * module's specs, so this is the discipline that replaces isolation.
 *
 * **Never name the migration ledger here** — `stx_migrations`, or whatever `stx.migrations.name`
 * moved it to. It is what records that a migration ran, and a migration does not run twice: emptying
 * it would leave the seed gone and the record of it gone too.
 */
suspend fun ReactiveMongoTemplate.clear(vararg collections: String) {
    collections.forEach { remove(Query(), it).awaitSingle() }
}
