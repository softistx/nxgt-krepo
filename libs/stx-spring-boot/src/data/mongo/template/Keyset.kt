package com.softistx.spring.data.mongo.template

import org.bson.BsonNull
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import org.springframework.data.mongodb.core.query.Criteria

/** One field of the ordering, named as the *database* names it, and which way it runs. */
internal data class SortKey(
    val field: String,
    val ascending: Boolean,
)

/** The `_id` every Mongo document has, and the tiebreak every keyset ordering ends with. */
internal const val ID_FIELD = "_id"

/**
 * The ordering a page is cut along, in storage field names, always ending in `_id`.
 *
 * **The tiebreak is not decoration.** Keyset pagination resumes from the last row's key, so the key
 * has to be unique — order by `name` alone and every document sharing a name is a coin toss between
 * being served twice and being skipped.
 *
 * **And the names are the stored ones.** A property carrying `@Field("nm")` is `nm` in the document,
 * so a cursor built from the property name would read nothing back and every page after the first
 * would be empty. [entity] is the mapping context's answer for the type being paged; a property it
 * does not know is used as written, which is what a caller naming a field inside a `Map` wants.
 */
internal fun sortKeys(
    sort: Sort,
    entity: MongoPersistentEntity<*>?,
): List<SortKey> {
    val keys =
        sort
            .map { order ->
                SortKey(entity.fieldNameOf(order.property), ascending = order.isAscending)
            }.toList()

    return if (keys.any { it.field == ID_FIELD }) keys else keys + SortKey(ID_FIELD, ascending = true)
}

/** What the database calls [property], which is not always what Kotlin calls it. */
internal fun MongoPersistentEntity<*>?.fieldNameOf(property: String): String = this?.getPersistentProperty(property)?.fieldName ?: property

/** The ordering as the server wants it, flipped when the page runs backward. */
internal fun sortOf(
    keys: List<SortKey>,
    forward: Boolean,
): Sort =
    Sort.by(
        keys.map {
            if (it.ascending == forward) Sort.Order.asc(it.field) else Sort.Order.desc(it.field)
        },
    )

/**
 * Everything strictly after [cursor] along [keys] — or strictly before it, when the page runs
 * backward.
 *
 * A single `$gt` only works when the ordering is one field. With more, "after" is lexicographic: the
 * first key is greater, *or* it is equal and the second is greater, and so on. That is the `$or`
 * this builds, and it is why a cursor carries every key rather than only the `_id`.
 */
internal fun keysetCriteria(
    cursor: Document,
    keys: List<SortKey>,
    forward: Boolean,
): Criteria {
    val branches =
        keys.mapIndexed { index, key ->
            val equalities = keys.take(index).map { Criteria.where(it.field).`is`(cursor[it.field]) }
            val comparison =
                Criteria.where(key.field).let {
                    if (key.ascending == forward) it.gt(cursor.valueOf(key.field)) else it.lt(cursor.valueOf(key.field))
                }
            if (equalities.isEmpty()) comparison else Criteria().andOperator(equalities + comparison)
        }

    return Criteria().orOperator(branches)
}

/**
 * A cursor key's value, with an explicit BSON null where the document has none.
 *
 * `Criteria.gt` takes a non-null `Any`, and a missing key is a real case — a sort field that is
 * nullable, or a document written before the field existed. `BsonNull` is how the driver spells the
 * value that `$gt` then compares against nothing, which is the same answer `stx-mongo` gives.
 */
private fun Document.valueOf(field: String): Any = this[field] ?: BsonNull.VALUE
