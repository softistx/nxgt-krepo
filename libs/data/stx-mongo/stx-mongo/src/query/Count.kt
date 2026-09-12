package com.softistx.mongo.query

import com.mongodb.client.model.CountOptions
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.BsonDocument
import org.bson.conversions.Bson

/**
 * How many documents match.
 *
 * `countDocuments` and not `estimatedDocumentCount`: the estimate reads collection metadata, which
 * is fast and is also wrong after an unclean shutdown, cannot take a filter, and cannot join a
 * session. A count that is allowed to be wrong is a decision for the caller to make explicitly.
 */
suspend fun <T : Any> MongoCollection<T>.count(
    filter: Bson = BsonDocument(),
    options: CountOptions = CountOptions(),
    session: ClientSession? = null,
): Long = session.select({ countDocuments(it, filter, options) }, { countDocuments(filter, options) })
