package com.softistx.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.softistx.mongo.codec.mongoCodecRegistry

/**
 * A client built the way this library needs one.
 *
 * ```kotlin
 * val client = mongoClient(uri = System.getenv("MONGO_URI"))
 * ```
 *
 * **The codec registry is the whole reason this exists.** A client built without
 * [mongoCodecRegistry] compiles, connects and reads — and then stores an `Instant` as something
 * nothing here can read back. Every step succeeds until the data is already written, which makes it
 * the kind of mistake a factory should make impossible rather than a README should warn about.
 *
 * It takes a URI and not a framework, so a worker, a CLI, a test or a DI container builds its client
 * the same way a server does. [configure] is for what this library has no opinion about: TLS, pool
 * sizes, read and write concerns.
 */
fun mongoClient(
    uri: String,
    configure: MongoClientSettings.Builder.() -> Unit = {},
): MongoClient = MongoClient.create(mongoClientSettings(uri, configure))

/**
 * The settings behind [mongoClient], for a caller that needs to build the client itself.
 *
 * The Reactive Streams client GridFS wants is the case in point: one pool behind both APIs means
 * one set of settings, and the registry has to be on both or a `GridFS` read decodes differently
 * from a collection read.
 */
fun mongoClientSettings(
    uri: String,
    configure: MongoClientSettings.Builder.() -> Unit = {},
): MongoClientSettings =
    MongoClientSettings
        .builder()
        .applyConnectionString(ConnectionString(uri))
        .codecRegistry(mongoCodecRegistry())
        .apply(configure)
        .build()
