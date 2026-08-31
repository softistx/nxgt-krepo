package com.strange.graphix.fixture

import com.strange.graphix.dataLoader
import com.strange.graphix.schema.Argument
import com.strange.graphix.schema.QueryMapping
import com.strange.graphix.schema.SchemaMapping
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class Book(
    val id: String,
    val authorId: String,
    val title: String,
)

@Serializable
data class Author(
    val id: String,
    val name: String,
)

data class ReviewKey(
    val bookId: String,
    val limit: Int,
)

class BookQueries {
    @QueryMapping
    fun book(): Book = Book("b1", "a1", "Dune")

    @QueryMapping
    fun books(): List<Book> =
        listOf(
            Book("b1", "a1", "Dune"),
            Book("b2", "a1", "Messiah"),
        )
}

class BookFields(
    private val authors: Map<String, Author> = mapOf("a1" to Author("a1", "Frank")),
    val authorLoads: AtomicInteger = AtomicInteger(),
    val reviewLoads: AtomicInteger = AtomicInteger(),
) {
    val authorsById =
        dataLoader<String, Author> { ids ->
            authorLoads.incrementAndGet()
            ids.mapNotNull { id -> authors[id]?.let { id to it } }.toMap()
        }

    val snippets =
        dataLoader<ReviewKey, List<String>> { keys ->
            reviewLoads.incrementAndGet()
            keys.associateWith { key -> (1..key.limit).map { "s$it-${key.bookId}" } }
        }

    @SchemaMapping
    suspend fun author(book: Book): Author? = authorsById.load(book.authorId)

    @SchemaMapping
    suspend fun snippets(
        book: Book,
        @Argument limit: Int = 2,
    ): List<String> = snippets.load(ReviewKey(book.id, limit)).orEmpty()
}

class DuplicateNamedLoaders {
    val one = dataLoader<String, String>("authorsById") { emptyMap() }
    val two = dataLoader<String, String>("authorsById") { emptyMap() }

    @SchemaMapping
    fun extra(book: Book): String = "x"
}

class EnvBookFields(
    val fieldNames: MutableList<String> = mutableListOf(),
) {
    val authorsById =
        dataLoader<String, Author> { ids, env ->
            fieldNames += env.field.name
            ids.mapNotNull { id -> if (id == "a1") id to Author("a1", "Frank") else null }.toMap()
        }

    @SchemaMapping
    suspend fun author(book: Book): Author? = authorsById.load(book.authorId)
}

class DelayedBookFields(
    val authorLoads: AtomicInteger = AtomicInteger(),
) {
    val authorsById =
        dataLoader<String, Author> { ids ->
            authorLoads.incrementAndGet()
            ids.mapNotNull { id -> if (id == "a1") id to Author("a1", "Frank") else null }.toMap()
        }

    @SchemaMapping
    suspend fun author(book: Book): Author? {
        delay(20)
        return authorsById.load(book.authorId)
    }
}
