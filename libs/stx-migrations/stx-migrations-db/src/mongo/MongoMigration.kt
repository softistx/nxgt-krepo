package com.softistx.migrations.db.mongo

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.migrations.Migration

/**
 * A migration that is handed a [MongoDatabase].
 *
 * ```kotlin
 * class V2Tags : MongoMigration {
 *     override val version = 2L
 *
 *     override suspend fun migrate(context: MongoDatabase) {
 *         context.getCollection<Document>("orders")
 *             .updateMany(Filters.exists("tags", false), Updates.set("tags", emptyList<String>()))
 *     }
 * }
 * ```
 *
 * An empty interface, and it earns its line. A generic `Migration<*>` erases: a Spring
 * `ObjectProvider` or any classpath scan could not tell a Mongo migration from a SQL one, so an
 * application with both stores would hand every migration to both runners and each would fail on the
 * other's context. This restores a distinct runtime type while the version, the description, the
 * ordering rule and the runner stay written once.
 */
interface MongoMigration : Migration<MongoDatabase>
