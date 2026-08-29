package com.strange.mongo.query

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.conversions.Bson

/**
 * The document as it is *after* the update.
 *
 * The driver's own default is [ReturnDocument.BEFORE], which is almost never what a service wants —
 * it updates and then answers with the state it just replaced. Overriding the default here is the
 * other reason these are not named after the driver's methods: shadowing would flip this back
 * without a word.
 */
private val RETURN_UPDATED = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)

suspend fun <T : Any> MongoCollection<T>.update(
    filter: Bson,
    update: Bson,
    options: UpdateOptions = UpdateOptions(),
    session: ClientSession? = null,
): UpdateResult = session.select({ updateOne(it, filter, update, options) }, { updateOne(filter, update, options) })

suspend fun <T : Any> MongoCollection<T>.updateById(
    id: Any,
    update: Bson,
    options: UpdateOptions = UpdateOptions(),
    session: ClientSession? = null,
): UpdateResult = update(byId(id), update, options, session)

suspend fun <T : Any> MongoCollection<T>.updateAll(
    filter: Bson,
    update: Bson,
    options: UpdateOptions = UpdateOptions(),
    session: ClientSession? = null,
): UpdateResult = session.select({ updateMany(it, filter, update, options) }, { updateMany(filter, update, options) })

/** Updates the first match and returns it as it now stands, or null when nothing matched. */
suspend fun <T : Any> MongoCollection<T>.findAndUpdate(
    filter: Bson,
    update: Bson,
    options: FindOneAndUpdateOptions = RETURN_UPDATED,
    session: ClientSession? = null,
): T? =
    session.select(
        { findOneAndUpdate(it, filter, update, options) },
        { findOneAndUpdate(filter, update, options) },
    )

suspend fun <T : Any> MongoCollection<T>.findByIdAndUpdate(
    id: Any,
    update: Bson,
    options: FindOneAndUpdateOptions = RETURN_UPDATED,
    session: ClientSession? = null,
): T? = findAndUpdate(byId(id), update, options, session)
