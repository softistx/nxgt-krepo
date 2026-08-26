package com.strange.mongo.query

import com.mongodb.client.model.InsertManyOptions
import com.mongodb.client.model.InsertOneOptions
import com.mongodb.client.result.InsertManyResult
import com.mongodb.client.result.InsertOneResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection

suspend fun <T : Any> MongoCollection<T>.insert(
    document: T,
    options: InsertOneOptions = InsertOneOptions(),
    session: ClientSession? = null,
): InsertOneResult = session.select({ insertOne(it, document, options) }, { insertOne(document, options) })

/** Inserting nothing is a no-op, not an `IllegalArgumentException` from the driver. */
suspend fun <T : Any> MongoCollection<T>.insertAll(
    documents: Collection<T>,
    options: InsertManyOptions = InsertManyOptions(),
    session: ClientSession? = null,
): InsertManyResult? {
    if (documents.isEmpty()) return null
    val ordered = documents.toList()
    return session.select({ insertMany(it, ordered, options) }, { insertMany(ordered, options) })
}
