package com.strange.ktor.mongo

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.ktor.required
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's Mongo client, as [MongoPlugin] opened it — for a second database, or a session. */
val Application.mongo: MongoClient get() = required(MongoKey, "MongoPlugin")

/** The database [MongoPlugin] was configured with. What a route almost always wants. */
val Application.database: MongoDatabase get() = required(MongoDatabaseKey, "MongoPlugin")

/** The application's Mongo client, from a route. */
val ApplicationCall.mongo: MongoClient get() = application.mongo

/** The configured database, from a route. */
val ApplicationCall.database: MongoDatabase get() = application.database
