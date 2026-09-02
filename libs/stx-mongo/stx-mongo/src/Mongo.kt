package com.softistx.mongo

import com.mongodb.kotlin.client.coroutine.MongoCluster
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase

/**
 * Typed handles onto a cluster.
 *
 * `getCollection<T>(name)` already exists on the driver; what it does not have is a way to name a
 * collection once and reach it from anywhere, which is what [collection] with a [CollectionName]
 * is for. A service that declares `object Users : CollectionName("users")` can no longer mistype
 * the name in one of the five places it queries.
 */
open class CollectionName(
    val value: String,
) {
    override fun toString(): String = value
}

inline fun <reified T : Any> MongoDatabase.collection(name: String): MongoCollection<T> = getCollection<T>(name)

inline fun <reified T : Any> MongoDatabase.collection(name: CollectionName): MongoCollection<T> = getCollection<T>(name.value)

/** Re-resolves [database] on this cluster — useful when a session was opened on another handle. */
fun MongoCluster.database(database: MongoDatabase): MongoDatabase = getDatabase(database.name)
