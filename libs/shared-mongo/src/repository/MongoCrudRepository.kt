package com.strange.mongo.repository

import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCluster
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.common.page.Page
import com.strange.mongo.CollectionName
import com.strange.mongo.DocumentNotFoundException
import com.strange.mongo.page.PaginationOptions
import com.strange.mongo.page.findPage
import com.strange.mongo.query.byId
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
import kotlin.reflect.KClass

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
 * class NoteRepository(database: MongoDatabase) :
 *     MongoCrudRepository<Note, String>(database, "notes", Note::class) {
 *     suspend fun findByTag(tag: String) = findAll(Filters.eq("tag", tag))
 *     override suspend fun ensureIndexes() { collection.ensureUniqueIndex(Indexes.ascending("tag")) }
 * }
 *
 * val notes = mongoRepository<Note, String>(database, "notes")   // no subclass wanted
 * ```
 *
 * **It takes the database, not a collection, so a container can build it.** A `MongoCollection<T>`
 * is derived from a database by a name and a type — which means a repository that asks for one
 * pushes both of those out to whoever wires it up, and `single { NoteRepository(get()) }` becomes
 * `single { NoteRepository(get<MongoDatabase>().collection<Note>("notes")) }` in every application
 * that uses it. Naming the collection is the repository's own business and belongs in its
 * declaration, once. The database is the injectable unit; the collection is derived here.
 *
 * A [MongoCluster] — which is what a `MongoClient` is — works too, with the database named beside
 * it, for a container that registers the client and nothing else.
 *
 * **[type] is a `KClass` and there is no way around it.** `getCollection<T>(name)` is `reified` and
 * a class cannot be: inside this one, `T` is not reifiable. So a subclass names its document class
 * once in its own declaration, and [mongoRepository] is the `reified` form for the plain case that
 * wants no subclass at all.
 *
 * Every method takes an optional [ClientSession] last, so the same repository serves a call inside
 * a transaction and one outside it.
 */
open class MongoCrudRepository<T : Any, ID : Any>(
    /** The database the collection is resolved on, and the handle a subclass reaches for anything else. */
    protected val database: MongoDatabase,
    /** The collection's name, for the messages a caller has to write. */
    val name: String,
    type: KClass<T>,
) {
    /** The same, named with a [CollectionName] so the string is written once for the application. */
    constructor(
        database: MongoDatabase,
        name: CollectionName,
        type: KClass<T>,
    ) : this(database, name.value, type)

    /**
     * The same, from a cluster — a `MongoClient` is one — with the database named beside it.
     *
     * For a container that registers the client rather than a database, which is the shape a
     * multi-database application ends up with.
     */
    constructor(
        cluster: MongoCluster,
        databaseName: String,
        name: String,
        type: KClass<T>,
    ) : this(cluster.getDatabase(databaseName), name, type)

    /** The collection this is over, resolved once — the handle is immutable and cheap to hold. */
    val collection: MongoCollection<T> = database.getCollection(name, type.java)

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

    /**
     * Inserts, and answers with the document as the collection now holds it.
     *
     * The read-back is what makes this different from [insert]: a default the collection applied, a
     * value a codec normalised, or an `_id` the *server* generated is part of what the caller now
     * has. This is what `MongoCrudService.create` is built on.
     *
     * **The id comes from the driver's own `InsertOneResult`**, not from reading one off the
     * document. That is why this class needs no `idOf`: a document whose `_id` the server assigned
     * reads back exactly as one that carried its own, and neither case asks the repository to know
     * where the key lives. An unacknowledged write concern reports no inserted id and nothing to
     * read back with, so the document is answered as it was built.
     */
    open suspend fun insertAndRead(
        document: T,
        session: ClientSession? = null,
    ): T {
        val inserted = collection.insert(document, session = session).insertedId ?: return document
        return collection.findOne(byId(inserted), session)
            ?: throw DocumentNotFoundException(name, inserted.toString())
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

/**
 * A repository over [T] with no subclass — the `reified` form of the constructor.
 *
 * ```kotlin
 * val notes = mongoRepository<Note, String>(database, "notes")
 * ```
 *
 * Both type arguments are written out because only [T] can be inferred from anything, and it is
 * inferred from nothing here. A collection that wants a query of its own wants a subclass instead,
 * where the document class is named once in the declaration.
 */
inline fun <reified T : Any, ID : Any> mongoRepository(
    database: MongoDatabase,
    name: String,
): MongoCrudRepository<T, ID> = MongoCrudRepository(database, name, T::class)

/** The same, named with a [CollectionName]. */
inline fun <reified T : Any, ID : Any> mongoRepository(
    database: MongoDatabase,
    name: CollectionName,
): MongoCrudRepository<T, ID> = MongoCrudRepository(database, name.value, T::class)

/** The same, from a cluster — a `MongoClient` is one — with the database named beside it. */
inline fun <reified T : Any, ID : Any> mongoRepository(
    cluster: MongoCluster,
    databaseName: String,
    name: String,
): MongoCrudRepository<T, ID> = MongoCrudRepository(cluster.getDatabase(databaseName), name, T::class)
