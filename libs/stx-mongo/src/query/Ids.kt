package com.softistx.mongo.query

import com.mongodb.client.model.Filters
import org.bson.conversions.Bson

/** Mongo's own primary key field. Spelling it once is the point. */
const val ID_FIELD: String = "_id"

/** `_id == id`. */
fun byId(id: Any): Bson = Filters.eq(ID_FIELD, id)

/** `_id in ids`. Matches nothing when [ids] is empty, which is what `$in: []` already means. */
fun byIds(ids: Collection<Any>): Bson = Filters.`in`(ID_FIELD, ids)
