package com.softistx.mongo.query

import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.FindFlow
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.softistx.mongo.DocumentNotFoundException
import kotlinx.coroutines.flow.firstOrNull
import org.bson.BsonDocument
import org.bson.conversions.Bson

/**
 * Reads, with an optional session.
 *
 * None of these is named after the driver method it wraps, and that is deliberate: a member
 * function always wins over an extension with the same signature, so a `find(filter)` extension
 * with a defaulted session would simply never be called for the two-argument form — silently, and
 * with the driver's own defaults instead of these. Different names, no shadowing.
 */
fun <T : Any> MongoCollection<T>.findAll(
    filter: Bson = BsonDocument(),
    session: ClientSession? = null,
): FindFlow<T> = session.select({ find(it, filter) }, { find(filter) })

/** The first match, or null. `limit(1)` so the server stops looking after it. */
suspend fun <T : Any> MongoCollection<T>.findOne(
    filter: Bson,
    session: ClientSession? = null,
): T? = findAll(filter, session).limit(1).firstOrNull()

suspend fun <T : Any> MongoCollection<T>.findById(
    id: Any,
    session: ClientSession? = null,
): T? = findOne(byId(id), session)

/**
 * [findById], for the caller that has nothing to say about a missing document — the vast majority.
 * Throws [DocumentNotFoundException] naming this collection and [id].
 */
suspend fun <T : Any> MongoCollection<T>.requireById(
    id: Any,
    session: ClientSession? = null,
): T = findById(id, session) ?: throw DocumentNotFoundException(namespace.collectionName, id.toString())

/** Documents whose `_id` is in [ids], in whatever order the server returns them. */
fun <T : Any> MongoCollection<T>.findByIds(
    ids: Collection<Any>,
    session: ClientSession? = null,
): FindFlow<T> = findAll(byIds(ids), session)
