package com.softistx.mongo.page

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.softistx.mongo.InvalidPaginationException
import com.softistx.mongo.query.ID_FIELD
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.bson.BsonDocument
import org.bson.BsonNull
import org.bson.BsonValue
import org.bson.conversions.Bson

/** One field of the ordering, and which way it runs. */
internal data class SortKey(
    val field: String,
    val ascending: Boolean,
)

/**
 * The ordering a page is cut along, always ending in `_id`.
 *
 * The `_id` tiebreak is not decoration. Keyset pagination resumes from the last row's key, so the
 * key has to be unique — order by `name` alone and every document sharing a name is a coin toss
 * between being served twice and being skipped.
 */
internal fun sortKeys(sort: JsonObject?): List<SortKey> {
    val keys =
        sort.orEmpty().map { (field, direction) ->
            val value =
                direction.jsonPrimitive.intOrNull
                    ?: throw InvalidPaginationException("sort direction for '$field' must be 1 or -1")
            when (value) {
                1 -> SortKey(field, ascending = true)
                -1 -> SortKey(field, ascending = false)
                else -> throw InvalidPaginationException("sort direction for '$field' must be 1 or -1, got $value")
            }
        }

    return if (keys.any { it.field == ID_FIELD }) keys else keys + SortKey(ID_FIELD, ascending = true)
}

/** The ordering as the server wants it, flipped when the page runs backward. */
internal fun sortOf(
    keys: List<SortKey>,
    forward: Boolean,
): Bson =
    Sorts.orderBy(
        keys.map { if (it.ascending == forward) Sorts.ascending(it.field) else Sorts.descending(it.field) },
    )

/**
 * Everything strictly after [cursor] along [keys] — or strictly before it, when the page runs
 * backward.
 *
 * A single `$gt` only works when the ordering is one field. With more, "after" is lexicographic:
 * the first key is greater, *or* it is equal and the second is greater, and so on. That is the
 * `$or` this builds, and it is why the cursor carries every key rather than just the `_id`.
 */
internal fun keysetFilter(
    cursor: BsonDocument,
    keys: List<SortKey>,
    forward: Boolean,
): Bson {
    val branches =
        keys.mapIndexed { index, key ->
            val equalities = keys.take(index).map { Filters.eq(it.field, cursor.valueOf(it.field)) }
            val comparison =
                if (key.ascending == forward) {
                    Filters.gt(key.field, cursor.valueOf(key.field))
                } else {
                    Filters.lt(key.field, cursor.valueOf(key.field))
                }
            if (equalities.isEmpty()) comparison else Filters.and(equalities + comparison)
        }

    return Filters.or(branches)
}

/** A key the cursor does not carry sorts as BSON null, which is where a missing field sorts too. */
private fun BsonDocument.valueOf(field: String): BsonValue = this[field] ?: BsonNull.VALUE
