package com.softistx.mongo.ktor

import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.ktor.publish
import com.softistx.ktor.resource
import com.softistx.mongo.mongoClient
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey

/**
 * One Mongo client for the application and one database handle over it, closed when it stops.
 *
 * ```kotlin
 * install(MongoDB) { uri = System.getenv("MONGO_URI"); database = "orders" }
 *
 * get("/orders/{id}") { call.respond(call.database.collection<Order>("orders").findById(id)) }
 * ```
 *
 * The client comes from [mongoClient], which lives in `stx-mongo` rather than here: the reason
 * it exists — a client built without the codec registry stores an `Instant` as something nothing in
 * that library can read back, and every step succeeds until the data is already written — is a fact
 * about the driver and not about the web framework. A worker or a CLI needs the same client, and
 * should not have to install a Ktor plugin to get one built correctly.
 *
 * The driver's client is a pool and is thread-safe, so one is right; [configure] is there for the
 * TLS, pool and read-concern settings a deployment has opinions about and this module should not.
 */
val MongoDB =
    createApplicationPlugin(name = "Mongo", createConfiguration = ::MongoDBConfiguration) {
        val name = requireNotNull(pluginConfig.database) { "install(MongoDB) needs `database`" }

        val client =
            application.resource(MongoKey, pluginConfig.instance) {
                val uri = requireNotNull(pluginConfig.uri) { "install(MongoDB) needs `uri` or `instance`" }
                mongoClient(uri, pluginConfig.configure)
            }

        application.publish(MongoDatabaseKey, client.getDatabase(name))
        if (pluginConfig.injectable) application.provideMongo()
    }

/** What [MongoDB] connects with. */
class MongoDBConfiguration {
    /** The connection string. Required unless [instance] is set — a default would be a guess about someone's cluster. */
    var uri: String? = null

    /** The database routes reach through `call.database`. Required for the same reason. */
    var database: String? = null

    /** Everything this module has no opinion about: TLS, pool sizes, read and write concerns. */
    var configure: MongoClientSettings.Builder.() -> Unit = {}

    /**
     * A client built elsewhere — by a DI container, or by hand.
     *
     * When set, [uri] and [configure] are not used and this is **not** closed when the application stops: whoever created
     * it closes it. That is what lets a container own the client while routes still reach it
     * through `call.mongo`.
     */
    var instance: MongoClient? = null

    /**
     * Registers the client and the database with Ktor's DI as well, so a class the container builds can take a
     * [MongoDatabase] in its constructor — the same one `call.database` hands a route.
     *
     * Off by default, and it has to be: `ktor-server-di` is compile-only in this module, so an
     * application that never asks for this must not be made to carry it at runtime. Setting it
     * calls [provideMongo], which lives in its own file for that reason — nothing loads a class
     * from Ktor's DI until the flag is true.
     *
     * The container closes what it hands out when the application stops, so this hands it a second
     * claim on closing the client. That is safe — these clients close idempotently — but a
     * client that has to outlive the application does not belong in it.
     */
    var injectable: Boolean = false
}

internal val MongoKey = AttributeKey<MongoClient>("com.mongodb.kotlin.client.coroutine.MongoClient")
internal val MongoDatabaseKey = AttributeKey<MongoDatabase>("com.mongodb.kotlin.client.coroutine.MongoDatabase")
