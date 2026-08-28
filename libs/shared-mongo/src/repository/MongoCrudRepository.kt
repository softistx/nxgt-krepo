package com.strange.mongo.repository

import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.strange.common.page.Page
import com.strange.mongo.page.PaginationOptions
import com.strange.mongo.page.findPage
import com.strange.mongo.query.count
import com.strange.mongo.query.deleteById
import com.strange.mongo.query.deleteByIds
import com.strange.mongo.query.existingIds
import com.strange.mongo.query.exists
import com.strange.mongo.query.existsById
import com.strange.mongo.query.findAll
import com.strange.mongo.query.findAndUpdate
import com.strange.mongo.query.findById
import com.strange.mongo.query.findByIdAndUpdate
import com.strange.mongo.query.findByIds
import com.strange.mongo.query.findOne
import com.strange.mongo.query.insert
import com.strange.mongo.query.insertAll
import com.strange.mongo.query.requireById
import kotlinx.coroutines.flow.Flow
import org.bson.BsonDocument
import org.bson.conversions.Bson

/**
 * One collection, as an object.
 *
 * The extensions in `com.strange.mongo.query` are the vocabulary; this is a noun that speaks it —
 * the thing a service holds, a test substitutes, and a subclass extends with the two or three
 * queries that are actually specific to a collection. Everything here is `open` for exactly that
 * reason, and nothing here has an opinion about *why* a document is being written, which is what
 * keeps it separate from `MongoCrudService`.
 *
 * ```kotlin
 * val notes = MongoCrudRepository(database.collection<Note>("notes"), Note::id)
 *
 * class NoteRepository(database: MongoDatabase) :
 *     MongoCrudRepository<Note, String>(database.collection("notes"), Note::id) {
 *     suspend fun findByTag(tag: String) = findAll(Filters.eq("tag", tag))
 *     override suspend fun ensureIndexes() { collection.ensureIndex(Indexes.ascending("tag")) }
 * }
 * ```
 *
 * [idOf] is a constructor parameter rather than an abstract method so the plain case needs no
 * subclass at all. The entity owns its `_id` — every write here takes the id from the document
 * instead of from a server-generated one, which is what lets `create` be "insert, then read back"
 * without a round trip to discover what was inserted.
 *
 * Every method takes an optional [ClientSession] last, so the same repository serves a call inside
 * a transaction and one outside it.
 */
open class MongoCrudRepository<T : Any, ID : Any>(
    val collection: MongoCollection<T>,
    val idOf: (T) -> ID,
) {
    /** The collection's name, for the messages a caller has to write. */
    val name: String get() = collection.namespace.collectionName

    // ─── Reads ────────────────────────────────────────────────────────────────

    open fun findAll(
        filter: Bson = BsonDocument(),
        session: ClientSession? = null,
    ): Flow<T> = collection.findAll(filter, session)

    open suspend fun findPage(
        options: PaginationOptions,
        session: ClientSession? = null,
    ): Page<T> = collection.findPage(options, session)

    open suspend fun findOne(
        filter: Bson,
        session: ClientSession? = null,
    ): T? = collection.findOne(filter, session)

    open suspend fun findById(
        id: ID,
        session: ClientSession? = null,
    ): T? = collection.findById(id, session)

    /** [findById], throwing `DocumentNotFoundException` instead of answering null. */
    open suspend fun requireById(
        id: ID,
        session: ClientSession? = null,
    ): T = collection.requireById(id, session)

    open fun findByIds(
        ids: Collection<ID>,
        session: ClientSession? = null,
    ): Flow<T> = collection.findByIds(ids, session)

    open suspend fun count(
        filter: Bson = BsonDocument(),
        session: ClientSession? = null,
    ): Long = collection.count(filter, session = session)

    open suspend fun exists(
        filter: Bson,
        session: ClientSession? = null,
    ): Boolean = collection.exists(filter, session)

    open suspend fun existsById(
        id: ID,
        session: ClientSession? = null,
    ): Boolean = collection.existsById(id, session)

    /** The subset of [ids] that exists, in the order given — one query, not one per id. */
    open suspend fun existingIds(
        ids: Collection<ID>,
        session: ClientSession? = null,
    ): List<ID> = collection.existingIds(ids, session)

    // ─── Writes ───────────────────────────────────────────────────────────────

    open suspend fun insert(
        document: T,
        session: ClientSession? = null,
    ): T {
        collection.insert(document, session = session)
        return document
    }

    open suspend fun insertAll(
        documents: Collection<T>,
        session: ClientSession? = null,
    ): List<T> {
        collection.insertAll(documents, session = session)
        return documents.toList()
    }

    /** The document as it stands after [update], or null when there was nothing to update. */
    open suspend fun updateById(
        id: ID,
        update: Bson,
        session: ClientSession? = null,
    ): T? = collection.findByIdAndUpdate(id, update, session = session)

    open suspend fun updateOne(
        filter: Bson,
        update: Bson,
        session: ClientSession? = null,
    ): T? = collection.findAndUpdate(filter, update, session = session)

    /** Whether there was a document to delete. */
    open suspend fun deleteById(
        id: ID,
        session: ClientSession? = null,
    ): Boolean = collection.deleteById(id, session = session).deletedCount > 0

    open suspend fun deleteByIds(
        ids: Collection<ID>,
        session: ClientSession? = null,
    ): Long = collection.deleteByIds(ids, session = session).deletedCount

    // ─── Startup ──────────────────────────────────────────────────────────────

    /**
     * Declares this collection's indexes. Nothing by default; override with `ensureIndex` calls and
     * have the application call it once at startup.
     */
    open suspend fun ensureIndexes() = Unit
}
