package com.strange.mongo.service

import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCluster
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.mongo.audit.AuditMetadata
import com.strange.mongo.audit.Audited
import com.strange.mongo.collection
import com.strange.mongo.repository.mongoRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson

@Serializable
internal data class Task(
    @SerialName("_id") val id: String,
    val title: String,
    val done: Boolean = false,
    override val metadata: AuditMetadata = AuditMetadata(),
) : Audited

internal data class NewTask(
    val id: String,
    val title: String,
)

internal data class EditTask(
    val title: String? = null,
    val done: Boolean? = null,
)

/**
 * The service from the KDoc, with every hook recording that it ran — which is how the tests assert
 * on ordering and on what a failing hook takes down with it.
 */
internal open class TaskService(
    database: MongoDatabase,
    principal: String? = null,
    transactions: MongoCluster? = null,
) : MongoCrudService<Task, String, NewTask, EditTask>(
        mongoRepository<Task, String>(database, "tasks"),
        principal,
        transactions,
    ) {
    val calls = mutableListOf<String>()

    override suspend fun buildCreate(input: NewTask): Task = Task(input.id, input.title, metadata = AuditMetadata.by(principal))

    override suspend fun buildUpdate(
        existing: Task,
        input: EditTask,
    ): List<Bson> =
        buildList {
            input.title?.let { add(Updates.set("title", it)) }
            input.done?.let { add(Updates.set("done", it)) }
        }

    override suspend fun beforeCreate(input: NewTask) {
        calls += "beforeCreate"
    }

    override suspend fun afterCreate(
        created: Task,
        session: ClientSession?,
    ) {
        calls += "afterCreate"
    }

    override suspend fun beforeUpdate(
        existing: Task,
        input: EditTask,
    ) {
        calls += "beforeUpdate"
    }

    override suspend fun afterUpdate(
        previous: Task,
        updated: Task,
        session: ClientSession?,
    ) {
        calls += "afterUpdate"
    }

    override suspend fun beforeDelete(
        ids: Collection<String>,
        session: ClientSession?,
    ) {
        calls += "beforeDelete"
    }

    override suspend fun afterDelete(
        ids: Collection<String>,
        session: ClientSession?,
    ) {
        calls += "afterDelete"
    }
}
