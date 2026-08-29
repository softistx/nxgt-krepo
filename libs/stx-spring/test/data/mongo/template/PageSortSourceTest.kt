package com.strange.spring.data.mongo.template

import com.strange.spring.data.mongo.filter.toSort
import com.strange.spring.web.SortOrder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Query

@Document("ranked")
internal data class Ranked(
    @Id val id: String,
    val rank: Int,
)

/**
 * A page's ordering has one source, and it is [MongoPage.sort].
 *
 * This was a real defect and the library's own README demonstrated it: `MongoPage.first(20, query =
 * request.mongoQuery)` puts the ordering on the *query*, where the keyset machinery cannot see it.
 * `Query.with` appends, so the rows come back correctly ordered and the first page looks right — but
 * the cursor is built from `sort`, which is empty, so it carries `_id` alone. Resuming from it asks
 * for `_id > "c"` against rows ordered by rank, and the answer is nothing at all.
 *
 * Measured before the guard: first page `[1, 2]`, cursor `{"_id": "c"}`, second page `[]`, with the
 * remaining row unreachable by any cursor. Every existing spec passed, because every existing spec
 * spelled it correctly — only the documentation was wrong, which is the kind of defect a type can
 * prevent and a reviewer cannot.
 */
class PageSortSourceTest :
    StringSpec({
        val bySort = listOf(SortOrder("rank")).toSort()

        "a query that carries its own sort is refused" {
            val failure = shouldThrow<IllegalArgumentException> { MongoPage.first(2, query = Query().with(bySort)) }

            failure.message!! shouldContain "sort ="
        }

        "an unsorted query is fine, which is every correct call" {
            MongoPage.first(2, query = Query(), sort = bySort).sort shouldBe bySort
        }

        "the ordering in `sort` pages all the way through".config(enabled = SpringMongo.available) {
            SpringMongo.withTemplate { template ->
                // Insertion order deliberately disagrees with rank order, so paging by `_id`
                // instead of by rank cannot accidentally produce the right answer.
                listOf(Ranked("a", 3), Ranked("b", 1), Ranked("c", 2))
                    .forEach { template.insert(it).awaitSingle() }

                val first = template.findPage<Ranked>(MongoPage.first(2, query = Query(), sort = bySort))
                first.data.map { it.rank } shouldBe listOf(1, 2)

                val second =
                    template.findPage<Ranked>(
                        MongoPage.first(2, cursor = first.info.endCursor, query = Query(), sort = bySort),
                    )

                // The row the defect lost.
                second.data.map { it.rank } shouldBe listOf(3)
            }
        }
    })
