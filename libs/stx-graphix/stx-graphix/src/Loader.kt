package com.strange.graphix

import com.strange.graphix.execute.DataFetchingEnvironmentElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await

/**
 * A coroutine DataLoader. Declare it on a mapping class; Graphix registers it per operation.
 *
 * The key may include field arguments (a `data class ReviewKey(val productId: String, val limit: Int)`).
 * Call [load] from a `suspend` `@SchemaMapping`. Graphix starts that resolver undispatched so
 * `load` queues the key before graphql-java dispatches, and uses exhausted DataLoader dispatch
 * so a `load` after other suspend work still completes.
 *
 * ```kotlin
 * class BookFields(private val authors: AuthorStore) {
 *     val authors = dataLoader<String, Author> { ids -> authors.find(ids).associateBy { it.id } }
 *
 *     @SchemaMapping
 *     suspend fun author(book: Book): Author? = authors.load(book.authorId)
 *
 *     @SchemaMapping
 *     suspend fun reviews(book: Book, limit: Int = 10): List<Review> =
 *         reviews.load(ReviewKey(book.id, limit)).orEmpty()
 * }
 * ```
 */
class Loader<K : Any, V : Any> internal constructor(
    name: String,
    internal val batch: suspend (List<K>) -> Map<K, V>,
) {
    /** GraphQL DataLoader name. Empty until schema build copies the property name onto this instance. */
    internal var name: String = name
        private set

    suspend fun load(key: K): V? {
        val loader = dataLoader()
        return loader.load(key).await()
    }

    suspend fun loadMany(keys: List<K>): List<V?> {
        val loader = dataLoader()
        return loader.loadMany(keys).await()
    }

    internal fun named(name: String): Loader<K, V> {
        if (this.name.isEmpty()) this.name = name
        return this
    }

    private suspend fun dataLoader(): org.dataloader.DataLoader<K, V> {
        val environment =
            currentCoroutineContext()[DataFetchingEnvironmentElement]?.environment
                ?: error("Loader.load must run inside a Graphix resolver")
        return environment.getDataLoader<K, V>(name)
            ?: error("no DataLoader '$name'")
    }
}

/**
 * Declares a DataLoader. [name] defaults to the property name when the instance is registered
 * with [GraphixBuilder.type] / [GraphixBuilder.query].
 *
 * [batch] receives the keys of this level and returns `Map<K, V>`. Missing keys become null.
 */
fun <K : Any, V : Any> dataLoader(
    name: String = "",
    batch: suspend (List<K>) -> Map<K, V>,
): Loader<K, V> = Loader(name, batch)
