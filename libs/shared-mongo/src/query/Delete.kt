package com.strange.mongo.query

import com.mongodb.client.model.DeleteOptions
import com.mongodb.client.model.FindOneAndDeleteOptions
import com.mongodb.client.result.DeleteResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.conversions.Bson

suspend fun <T : Any> MongoCollection<T>.delete(
    filter: Bson,
    options: DeleteOptions = DeleteOptions(),
    session: ClientSession? = null,
): DeleteResult = session.select({ deleteOne(it, filter, options) }, { deleteOne(filter, options) })

suspend fun <T : Any> MongoCollection<T>.deleteById(
    id: Any,
    options: DeleteOptions = DeleteOptions(),
    session: ClientSession? = null,
): DeleteResult = delete(byId(id), options, session)

suspend fun <T : Any> MongoCollection<T>.deleteAll(
    filter: Bson,
    options: DeleteOptions = DeleteOptions(),
    session: ClientSession? = null,
): DeleteResult = session.select({ deleteMany(it, filter, options) }, { deleteMany(filter, options) })

suspend fun <T : Any> MongoCollection<T>.deleteByIds(
    ids: Collection<Any>,
    options: DeleteOptions = DeleteOptions(),
    session: ClientSession? = null,
): DeleteResult = deleteAll(byIds(ids), options, session)

/** Deletes the first match and hands back what was deleted, or null when nothing matched. */
suspend fun <T : Any> MongoCollection<T>.findAndDelete(
    filter: Bson,
    options: FindOneAndDeleteOptions = FindOneAndDeleteOptions(),
    session: ClientSession? = null,
): T? = session.select({ findOneAndDelete(it, filter, options) }, { findOneAndDelete(filter, options) })

suspend fun <T : Any> MongoCollection<T>.findByIdAndDelete(
    id: Any,
    options: FindOneAndDeleteOptions = FindOneAndDeleteOptions(),
    session: ClientSession? = null,
): T? = findAndDelete(byId(id), options, session)
