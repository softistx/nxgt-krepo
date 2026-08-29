package com.strange.mongo

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.kotlinx.ObjectIdSerializer
import org.bson.types.ObjectId

/**
 * One entity for every spec that needs a collection. `@SerialName("_id")` is how a Kotlin-shaped
 * `id` reaches Mongo's own key field, so the fixture also stands as the example.
 */
@Serializable
internal data class Note(
    @SerialName("_id") val id: String,
    val text: String,
    val tag: String = "note",
)

/**
 * A document that leaves its `_id` to the server, for the specs about reading one back.
 *
 * A null `_id` is omitted rather than written — `BsonConfiguration.explicitNulls` is false by
 * default — so Mongo assigns an `ObjectId`, which is the case a repository reading the id off the
 * document in hand could never have covered.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class Draft(
    @SerialName("_id") @Serializable(with = ObjectIdSerializer::class) val id: ObjectId? = null,
    val text: String = "",
)

/** A `notes` collection in a database of its own, dropped when [block] returns. */
internal suspend fun withNotes(block: suspend (MongoCollection<Note>) -> Unit) =
    MongoTestCluster.withDatabase { _, database -> block(database.collection<Note>("notes")) }

/** [withNotes] for the specs that also need the client the collection came from — sessions. */
internal suspend fun withNotesAndClient(block: suspend (MongoCollection<Note>, MongoClient) -> Unit) =
    MongoTestCluster.withDatabase { client, database -> block(database.collection<Note>("notes"), client) }

/** A `drafts` collection, for the specs about an `_id` the server assigns. */
internal suspend fun withDrafts(block: suspend (MongoCollection<Draft>) -> Unit) =
    MongoTestCluster.withDatabase { _, database -> block(database.collection<Draft>("drafts")) }
