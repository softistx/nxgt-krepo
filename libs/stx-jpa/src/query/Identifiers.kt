package com.softistx.jpa.query

import com.softistx.jpa.criteria.eq
import com.softistx.jpa.criteria.get
import com.softistx.jpa.criteria.oneOf
import com.softistx.jpa.session.JpaQueries
import kotlin.reflect.KProperty1

/*
 * Reads that restrict on the identifier column, and so need to be told which property that is.
 *
 * `Purchase::id` gives the name for the restriction and the entity for the query in one value — the
 * receiver's type argument is where `T` comes from, so nothing here has to work out at runtime what
 * it is generic over. A single row by identifier needs none of this: `find` and `get` on the session
 * take the id alone.
 */

/** The rows whose identifier is one of [values]. Asking for none queries nothing. */
suspend inline fun <reified T : Any, ID : Any> JpaQueries.findByIds(
    id: KProperty1<T, ID>,
    values: Collection<ID>,
): List<T> = if (values.isEmpty()) emptyList() else findAll(spec = { it[id] oneOf values })

/** Whether the row is there, asked the cheap way [exists] asks. */
suspend inline fun <reified T : Any, ID : Any> JpaQueries.existsById(
    id: KProperty1<T, ID>,
    value: ID,
): Boolean = exists<T> { it[id] eq value }

/**
 * The subset of [values] that exists, one query returning one column — not one row per id, and not
 * the entities themselves. What a caller wants before deleting or reporting on a batch of ids.
 *
 * `ID` is reified so the projection has a class to target without asking Hibernate's metamodel for
 * one. It is [javaObjectType][kotlin.reflect.KClass.javaObjectType] rather than `java`, because
 * `Long::class.java` is `long.class` and a criteria selecting a nullable column has to project into
 * the boxed type. `IdentifierTest` pins that with a `Long` identifier.
 */
suspend inline fun <reified T : Any, reified ID : Any> JpaQueries.existingIds(
    id: KProperty1<T, ID>,
    values: Collection<ID>,
): List<ID> {
    if (values.isEmpty()) return emptyList()

    val criteria = this.criteria.createQuery(ID::class.javaObjectType)
    val root = criteria.from(T::class.java)

    criteria.select(root[id])
    criteria.where(root[id] oneOf values)

    return query(criteria).list()
}
