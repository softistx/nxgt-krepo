package com.strange.mongo.query

import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Projections
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.bson.conversions.Bson

/** Whether anything matches [filter]. Counts with `limit(1)`: the number is not the question. */
suspend fun <T : Any> MongoCollection<T>.exists(
    filter: Bson,
    session: ClientSession? = null,
): Boolean = count(filter, CountOptions().limit(1), session) > 0

suspend fun <T : Any> MongoCollection<T>.existsById(
    id: Any,
    session: ClientSession? = null,
): Boolean = exists(byId(id), session)

/**
 * The subset of [ids] that exists, in the order given.
 *
 * One query, not one per id — the obvious `ids.filter { existsById(it) }` is a round trip each and
 * turns a validation step over a list of references into the slowest thing in the request.
 *
 * The lookup reads raw [Document]s rather than `T`: only `_id` is projected, and a projection that
 * drops required fields is not decodable as the entity.
 */
suspend fun <T : Any, ID : Any> MongoCollection<T>.existingIds(
    ids: Collection<ID>,
    session: ClientSession? = null,
): List<ID> {
    if (ids.isEmpty()) return emptyList()

    val filter = byIds(ids)
    val documents = withDocumentClass<Document>()
    val found =
        session
            .select({ documents.find(it, filter) }, { documents.find(filter) })
            .projection(Projections.include(ID_FIELD))
            .toList()
            .mapNotNullTo(mutableSetOf()) { it[ID_FIELD] }

    return ids.filter { it in found }
}
