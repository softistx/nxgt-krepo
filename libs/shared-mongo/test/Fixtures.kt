package com.strange.mongo

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

/** A `notes` collection in a database of its own, dropped when [block] returns. */
internal suspend fun withNotes(block: suspend (MongoCollection<Note>) -> Unit) =
    MongoTestCluster.withDatabase { _, database -> block(database.collection<Note>("notes")) }

/** [withNotes] for the specs that also need the client the collection came from — sessions. */
internal suspend fun withNotesAndClient(block: suspend (MongoCollection<Note>, MongoClient) -> Unit) =
    MongoTestCluster.withDatabase { client, database -> block(database.collection<Note>("notes"), client) }
