package com.softistx.mongo.ktor

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Makes the plugin's client, and the database over it, injectable — without opening a second one.
 *
 * ```kotlin
 * install(MongoDB) { uri = …; database = "orders" }
 * provideMongo()
 *
 * class OrderRepository(private val orders: MongoDatabase)   // built by the container, no ApplicationCall in sight
 * ```
 *
 * Or in one line, which is the same thing: `install(MongoDB) { uri = …; database = "orders"; injectable = true }`.
 *
 * **The container closes it at application stop, and that is not a problem.** Ktor's DI closes every
 * `AutoCloseable` it hands out — one a provider merely passed through included, which a spec in
 * this module pins, and a per-key `cleanup` runs in addition to that rather than instead of it. So
 * this resource is closed by the container as well as by whoever created it, and both are safe
 * because these clients close idempotently: see `CloseGuard` in `stx-common`. What it does mean
 * is that a connection which has to outlive the application should not be registered here.
 */
fun Application.provideMongo() {
    val client = mongo
    val orders = database
    dependencies {
        provide<MongoClient> { client }
        provide<MongoDatabase> { orders }
    }
}
