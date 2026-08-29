package com.strange.spring.data.mongo.template

import com.strange.common.page.Page
import com.strange.common.page.pageOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.bson.Document
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import kotlin.reflect.KClass

/**
 * One page of [T], and the cursors needed to ask for the next or previous one.
 *
 * ```kotlin
 * val page = template.findPage<Order>(request.mongoPage())
 * ```
 *
 * **Keyset, not `skip`.** An offset page re-reads every row it skips, so page 500 costs five hundred
 * pages of work, and a row inserted while a client is paging shifts every later page by one — the
 * client sees a row twice or never. A cursor resumes from a key instead: constant cost, and stable
 * under writes.
 *
 * **Documents are fetched raw and decoded here**, rather than letting the template map them. The
 * cursor is built from the *stored* values of the sort keys, and a mapped object no longer has them
 * — a `@Field("nm")` property, a value an `@ReadingConverter` transformed. Reading the `Document`
 * gives both: the key for the cursor, and `T` through the same converter the template would have
 * used.
 *
 * One row beyond the page is fetched, and that is the whole answer to *is there another page* — a
 * row rather than a second query. `stx-common`'s `pageOf` trims it and turns it into the flags.
 */
suspend inline fun <reified T : Any> ReactiveMongoTemplate.findPage(window: MongoPage): Page<T> = findPage(T::class, window)

/**
 * [findPage] for a type that is not known statically — a route dispatching on a path segment, a
 * generic list endpoint.
 *
 * It exists because the reified form has to be `inline`, and an inline function cannot reach the
 * cursor and keyset machinery, which has no business being public. Making this the real
 * implementation keeps that machinery `internal` and leaves the reified form a single line, rather
 * than marking five functions `@PublishedApi` and publishing them by accident.
 */
suspend fun <T : Any> ReactiveMongoTemplate.findPage(
    type: KClass<T>,
    window: MongoPage,
): Page<T> {
    val java = type.java
    val keys = sortKeys(window.sort, converter.mappingContext.getPersistentEntity(java))
    val query = Query.of(window.query)

    window.cursor?.let { query.addCriteria(keysetCriteria(decodeCursor(it, keys), keys, window.forward)) }
    query.with(sortOf(keys, window.forward))
    // One more than asked for: the extra row is what says whether there is another page.
    window.limit?.let { query.limit(it + 1) }

    val rows =
        find(query, Document::class.java, getCollectionName(java))
            .asFlow()
            .toList()

    return pageOf(
        rows = rows,
        limit = window.limit,
        forward = window.forward,
        resumed = window.cursor != null,
        cursorOf = { encodeCursor(it, keys) },
        valueOf = { converter.read(java, it) },
    )
}
