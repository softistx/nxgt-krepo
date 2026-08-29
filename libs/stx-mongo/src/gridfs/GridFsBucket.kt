package com.strange.mongo.gridfs

import com.mongodb.client.gridfs.model.GridFSDownloadOptions
import com.mongodb.client.gridfs.model.GridFSFile
import com.mongodb.client.gridfs.model.GridFSUploadOptions
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.reactivestreams.client.MongoDatabase
import com.mongodb.reactivestreams.client.gridfs.GridFSBuckets
import com.strange.mongo.query.byId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.bson.BsonDocument
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import com.mongodb.reactivestreams.client.gridfs.GridFSBucket as ReactiveGridFsBucket

/**
 * GridFS, as suspending functions.
 *
 * The Kotlin coroutine driver has no GridFS at all — the API stops at collections — so this wraps
 * the Reactive Streams bucket, which is the same one the coroutine driver is itself built on.
 *
 * That matters at construction. Build the reactive client *first* and hand it to both APIs, and
 * everything shares one connection pool:
 *
 * ```kotlin
 * val reactive = MongoClients.create(settings)
 * val client = MongoClient(reactive)                                   // the coroutine API
 * val files = GridFsBucket.of(reactive.getDatabase("app"), "uploads")  // the same pool
 * ```
 *
 * Creating a second client for the bucket also works, and costs a second pool, a second monitor and
 * a second view of the cluster for no benefit.
 *
 * A [ClientSession] here is the coroutine one, so a caller inside `withTransaction` passes the same
 * session it passes everywhere else. GridFS writes are only transactional on a replica set, and
 * only for the metadata and chunk inserts — there is no rollback of a half-written upload beyond
 * what the transaction itself undoes.
 */
class GridFsBucket(
    private val wrapped: ReactiveGridFsBucket,
) {
    val bucketName: String get() = wrapped.bucketName

    val chunkSizeBytes: Int get() = wrapped.chunkSizeBytes

    fun withChunkSizeBytes(chunkSizeBytes: Int): GridFsBucket = GridFsBucket(wrapped.withChunkSizeBytes(chunkSizeBytes))

    // ─── Reads ────────────────────────────────────────────────────────────────

    fun find(
        filter: Bson = BsonDocument(),
        session: ClientSession? = null,
    ): Flow<GridFSFile> = (session?.let { wrapped.find(it.wrapped, filter) } ?: wrapped.find(filter)).asFlow()

    suspend fun findById(
        id: ObjectId,
        session: ClientSession? = null,
    ): GridFSFile? = find(byId(id), session).firstOrNull()

    /** The newest revision of [filename], or null. */
    suspend fun findByFilename(
        filename: String,
        session: ClientSession? = null,
    ): GridFSFile? = find(Document("filename", filename), session).firstOrNull()

    /**
     * Every byte of the file, or null when there is no such file.
     *
     * The existence check is a round trip that a bare download would not cost, and it buys the only
     * thing a caller can act on: `MongoGridFSException` covers a missing file and a corrupt one
     * alike, so catching it to return null would turn real corruption into an empty 404.
     */
    suspend fun download(
        id: ObjectId,
        session: ClientSession? = null,
    ): ByteArray? {
        findById(id, session) ?: return null
        return (session?.let { wrapped.downloadToPublisher(it.wrapped, id) } ?: wrapped.downloadToPublisher(id))
            .readBytes()
    }

    /** [download] by name. [revision] follows GridFS: 0 is the oldest, -1 the newest. */
    suspend fun download(
        filename: String,
        revision: Int = -1,
        session: ClientSession? = null,
    ): ByteArray? {
        val options = GridFSDownloadOptions().revision(revision)
        findByFilename(filename, session) ?: return null
        return (
            session?.let { wrapped.downloadToPublisher(it.wrapped, filename, options) }
                ?: wrapped.downloadToPublisher(filename, options)
        ).readBytes()
    }

    // ─── Writes ───────────────────────────────────────────────────────────────

    /**
     * Stores [bytes] under [filename] and answers with the id it was stored as.
     *
     * GridFS does not treat a filename as a key: uploading the same name twice stores two files and
     * leaves both, which is what [download]'s `revision` is for.
     */
    suspend fun upload(
        filename: String,
        bytes: ByteArray,
        metadata: Document? = null,
        session: ClientSession? = null,
    ): ObjectId {
        val options = GridFSUploadOptions().apply { metadata?.let { metadata(it) } }
        val source = bytes.asPublisher()
        val id =
            session?.let { wrapped.uploadFromPublisher(it.wrapped, filename, source, options) }
                ?: wrapped.uploadFromPublisher(filename, source, options)
        return id.awaitFirstOrNull() ?: error("GridFS accepted the upload of '$filename' without an id")
    }

    suspend fun delete(
        id: ObjectId,
        session: ClientSession? = null,
    ) = (session?.let { wrapped.delete(it.wrapped, id) } ?: wrapped.delete(id)).await()

    suspend fun rename(
        id: ObjectId,
        filename: String,
        session: ClientSession? = null,
    ) = (session?.let { wrapped.rename(it.wrapped, id, filename) } ?: wrapped.rename(id, filename)).await()

    /** Drops the bucket's two collections. */
    suspend fun drop(session: ClientSession? = null) = (session?.let { wrapped.drop(it.wrapped) } ?: wrapped.drop()).await()

    companion object {
        /** The bucket on [database] — the default `fs` bucket when [bucketName] is null. */
        fun of(
            database: MongoDatabase,
            bucketName: String? = null,
        ): GridFsBucket =
            GridFsBucket(
                bucketName?.let { GridFSBuckets.create(database, it) } ?: GridFSBuckets.create(database),
            )
    }
}
