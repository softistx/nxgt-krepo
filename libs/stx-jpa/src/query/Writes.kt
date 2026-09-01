package com.softistx.jpa.query

import com.softistx.jpa.JpaOutsideTransactionException
import com.softistx.jpa.session.JpaSession
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/*
 * The write verbs, each of which refuses a session that cannot write.
 *
 * `persist`, `merge` and `remove` on the session are JPA's own primitives and say what JPA says:
 * the instance is managed now, and the statement goes out when the session flushes. These say
 * something stronger — the row was written — so they are the ones that check.
 */

/**
 * Refuses a session with no transaction, with [JpaOutsideTransactionException].
 *
 * A session flushes at the end of a unit of work if and only if there is a transaction, so a
 * `persist` inside a plain `session { }` reaches no table — no error, no warning, no row — and
 * [deleteById] would answer `true` for a row it did not delete. The alternative to refusing is a
 * create that returns an entity, reports success, and wrote nothing.
 *
 * Reads are deliberately unguarded: reading outside a transaction is an ordinary thing to want, and
 * is what `session { }` is for.
 */
@PublishedApi
internal fun JpaSession.requireTransaction(
    operation: String,
    type: KClass<*>,
) {
    if (raw.currentTransaction() == null) throw JpaOutsideTransactionException(operation, type)
}

/** Makes it managed. It reaches the database when the session flushes, not here. */
suspend inline fun <reified T : Any> JpaSession.insert(instance: T): T {
    requireTransaction("insert", T::class)
    return instance.also { persist(it) }
}

/** The same, for a batch. */
suspend inline fun <reified T : Any> JpaSession.insertAll(instances: Collection<T>): List<T> {
    requireTransaction("insertAll", T::class)
    // `toTypedArray` is reified and the element type here is not what persist wants; `persist` takes
    // `Any`, so the array is built as one.
    val all = instances.toList()
    persist(*Array<Any>(all.size) { all[it] })
    return all
}

/** Copies a detached instance onto the managed one, and answers with that — not with the argument. */
suspend inline fun <reified T : Any> JpaSession.update(instance: T): T {
    requireTransaction("update", T::class)
    return merge(instance)
}

/** Deletes a managed instance. */
suspend inline fun <reified T : Any> JpaSession.delete(instance: T) {
    requireTransaction("delete", T::class)
    remove(instance)
}

/**
 * Whether there was a row to delete.
 *
 * It loads the row and removes it, rather than issuing a bulk `delete` on the identifier. A bulk
 * statement goes straight to the database: no cascade fires, no `@PreRemove` runs, and a copy already
 * loaded in this session keeps existing. One extra select buys all three back. The bulk form is still
 * a `createDelete<T>()` away for a caller who has measured and wants it.
 */
suspend inline fun <reified T : Any> JpaSession.deleteById(id: Any): Boolean {
    requireTransaction("deleteById", T::class)
    return find<T>(id)?.also { delete(it) } != null
}

/** How many of [values] were actually there. Loads them first, for the reason [deleteById] does. */
suspend inline fun <reified T : Any, ID : Any> JpaSession.deleteByIds(
    id: KProperty1<T, ID>,
    values: Collection<ID>,
): Int {
    requireTransaction("deleteByIds", T::class)
    return findByIds(id, values).onEach { delete(it) }.size
}
