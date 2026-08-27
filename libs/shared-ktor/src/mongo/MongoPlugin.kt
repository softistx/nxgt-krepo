package com.strange.ktor.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.ktor.own
import com.strange.mongo.codec.mongoCodecRegistry
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey

/**
 * One Mongo client for the application and one database handle over it, closed when it stops.
 *
 * ```kotlin
 * install(MongoPlugin) { uri = System.getenv("MONGO_URI"); database = "orders" }
 *
 * get("/orders/{id}") { call.respond(call.database.collection<Order>("orders").findById(id)) }
 * ```
 *
 * **The codec registry is the reason this exists.** A `MongoClient` built without
 * [mongoCodecRegistry] compiles, connects and reads — and then stores an `Instant` as something the
 * rest of this library cannot read back. Getting that wrong is silent at every step until the data
 * is already written, so the plugin does it rather than each service remembering to.
 *
 * The driver's client is a pool and is thread-safe, so one is right; [configure] is there for the
 * TLS, pool and read-concern settings a deployment has opinions about and this module should not.
 */
val MongoPlugin =
    createApplicationPlugin(name = "Mongo", createConfiguration = ::MongoPluginConfiguration) {
        val uri = requireNotNull(pluginConfig.uri) { "install(MongoPlugin) needs `uri`" }
        val name = requireNotNull(pluginConfig.database) { "install(MongoPlugin) needs `database`" }

        val client =
            MongoClient.create(
                MongoClientSettings
                    .builder()
                    .applyConnectionString(ConnectionString(uri))
                    .codecRegistry(mongoCodecRegistry())
                    .apply(pluginConfig.configure)
                    .build(),
            )

        application.own(MongoKey, client)
        application.attributes.put(MongoDatabaseKey, client.getDatabase(name))
    }

/** What [MongoPlugin] connects with. */
class MongoPluginConfiguration {
    /** The connection string. Required — a default here would be a guess about someone's cluster. */
    var uri: String? = null

    /** The database routes reach through `call.database`. Required for the same reason. */
    var database: String? = null

    /** Everything this module has no opinion about: TLS, pool sizes, read and write concerns. */
    var configure: MongoClientSettings.Builder.() -> Unit = {}
}

internal val MongoKey = AttributeKey<MongoClient>("com.mongodb.kotlin.client.coroutine.MongoClient")
internal val MongoDatabaseKey = AttributeKey<MongoDatabase>("com.mongodb.kotlin.client.coroutine.MongoDatabase")
