package com.strange.koin.mongo

import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.mongo.mongoClient
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * One Mongo client, and the database over it, for the container to hand out.
 *
 * ```kotlin
 * startKoin { modules(mongoModule(System.getenv("MONGO_URI"), database = "orders")) }
 *
 * class OrderRepository(private val orders: MongoDatabase)
 * ```
 *
 * Both are registered because both are worth injecting: a repository wants the database, a health
 * check or a migration wants the client. Only the client is closed — the database handle is a view
 * onto it, and closing one closes both.
 *
 * The client comes from [mongoClient] in `shared-mongo` rather than from the driver directly, for
 * the codec-registry reason that factory documents: build one without it and an `Instant` is stored
 * as something nothing in that library can read back, with every step succeeding on the way.
 *
 * In an application that also serves HTTP: `install(MongoDB) { instance = get(); database = "orders" }`.
 */
fun mongoModule(
    uri: String,
    database: String,
    configure: MongoClientSettings.Builder.() -> Unit = {},
): Module =
    module {
        single { mongoClient(uri, configure) } onClose { it?.close() }
        single { get<MongoClient>().getDatabase(database) }
    }
