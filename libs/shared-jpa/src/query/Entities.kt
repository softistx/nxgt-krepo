package com.strange.jpa.query

import com.strange.jpa.Jpa
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction

/**
 * One operation, one transaction.
 *
 * These are for the call that has nothing else to do: a handler that stores one entity, a lookup
 * that reads one row. Anything that touches the database twice belongs in a `transaction { }` of its
 * own — two of these are two transactions, and a caller who wanted one and got two has a bug that
 * only shows under a partial failure.
 */
suspend fun Jpa.persist(vararg entities: Any): Unit = transaction { it.persist(*entities) }

/** Copies a detached instance's state onto the managed one, and answers with that. */
suspend fun <T : Any> Jpa.merge(entity: T): T = transaction { it.merge(entity) }

/**
 * Deletes them, whichever session they came from.
 *
 * Every entity that reaches here is detached — one operation is one transaction, so the session that
 * loaded it closed with the transaction before this one opened — and Hibernate refuses a detached
 * instance with *"Unmanaged instance passed to remove()"*. So each is merged into this transaction
 * first, which costs a select per entity. [removeById] costs the same and says what it means when
 * the id is all there is.
 */
suspend fun Jpa.remove(vararg entities: Any): Unit =
    transaction { session ->
        entities.forEach { entity -> session.remove(session.merge(entity)) }
    }

/** By id, or null. */
suspend inline fun <reified T : Any> Jpa.find(id: Any): T? = session { it.find<T>(id) }

/** By id, or [JpaNotFoundException]. */
suspend inline fun <reified T : Any> Jpa.get(id: Any): T = session { it.get<T>(id) }

/**
 * Deletes by id, and answers whether there was anything to delete.
 *
 * It loads the entity first, which a `delete from` in HQL would not: that is what makes the cascades
 * and the `@PreRemove` callbacks fire, and what makes the answer trustworthy. When neither matters
 * and the volume does, `mutate("delete from …")` is one statement instead of two.
 */
suspend inline fun <reified T : Any> Jpa.removeById(id: Any): Boolean =
    transaction { session ->
        val entity = session.find<T>(id)
        if (entity != null) session.remove(entity)
        entity != null
    }
