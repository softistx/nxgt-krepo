package com.strange.spring.data.mongo.template

import com.mongodb.client.result.DeleteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.exists
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.remove
import kotlin.reflect.KProperty

// The handful of `ReactiveMongoTemplate` calls that are a `Flux` and an `awaitSingle` away from
// being readable. Nothing here adds behaviour — it makes a suspending call look like one.

/** The matching documents as a `Flow`, which is what a coroutine wants from a `Flux`. */
inline fun <reified T : Any> ReactiveMongoTemplate.findAsFlow(query: Query): Flow<T> = find<T>(query).asFlow()

/** Whether anything matches, suspending. */
suspend inline fun <reified T : Any> ReactiveMongoTemplate.existsBy(query: Query): Boolean = exists<T>(query).awaitSingle()

/** Documents whose [field] is one of [values]. Duplicates are dropped; the index does not want them either. */
inline fun <reified T : Any> ReactiveMongoTemplate.findWhereIn(
    field: String,
    values: Collection<Any>,
): Flow<T> = findAsFlow(Query(Criteria.where(field).`in`(values.toSet())))

/** [findWhereIn], with the compiler holding the field name. */
inline fun <reified T : Any> ReactiveMongoTemplate.findWhereIn(
    field: KProperty<*>,
    values: Collection<Any>,
): Flow<T> = findWhereIn<T>(field.name, values)

/**
 * Everything in the collection, gone.
 *
 * Named for what it does rather than made easy to reach by accident: there is no `remove()` here
 * that means this.
 */
suspend inline fun <reified T : Any> ReactiveMongoTemplate.removeAll(): DeleteResult = remove<T>().all().awaitSingle()

/**
 * Several update builders folded into one document.
 *
 * ```kotlin
 * val changes = updates({ it.set("name", name) }, { it.inc("version", 1) })
 * ```
 *
 * Each function receives the `Update` built so far, which is what makes a conditional step — one
 * that adds nothing when there is nothing to add — a function that returns its argument.
 */
fun updates(vararg steps: (Update) -> Update): Update = steps.fold(Update()) { built, step -> step(built) }
