package com.strange.mongo.query

import com.mongodb.client.model.InsertManyOptions
import com.mongodb.client.model.InsertOneOptions
import com.mongodb.client.result.InsertManyResult
import com.mongodb.client.result.InsertOneResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.strange.mongo.DocumentNotFoundException

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

/**
 * Inserts, and answers with the document as the collection now holds it.
 *
 * The read-back is what makes this different from [insert]: a default the collection applied, a
 * value a codec normalised, or an `_id` the *server* generated is part of what the caller now has.
 *
 * ```kotlin
 * val stored = notes.insertAndRead(Note(text = "anvil"))
 * ```
 *
 * **The id comes from the driver's own `InsertOneResult`**, not from reading one off the document.
 * So nothing here needs to know where the key lives: a document whose `_id` the server assigned
 * reads back exactly as one that carried its own. An unacknowledged write concern reports no
 * inserted id and so leaves nothing to read back with, and the document is answered as it was built.
 *
 * It is two round trips. [insert] is the one to reach for when the document in hand is already the
 * whole answer.
 */
suspend fun <T : Any> MongoCollection<T>.insertAndRead(
    document: T,
    options: InsertOneOptions = InsertOneOptions(),
    session: ClientSession? = null,
): T {
    val inserted = insert(document, options, session).insertedId ?: return document
    return findOne(byId(inserted), session)
        ?: throw DocumentNotFoundException(namespace.collectionName, inserted.toString())
}
