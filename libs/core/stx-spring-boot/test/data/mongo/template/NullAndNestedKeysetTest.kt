package com.softistx.spring.data.mongo.template

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.mapping.Document

@Document("rows")
private data class Row(
    @Id val id: String,
    val rank: Int? = null,
)

/** A stored null (`b`, `d`), a missing field (`c`) and values, so every kind of null is walked. */
private val RAW =
    listOf(
        mapOf("_id" to "a", "rank" to 2),
        mapOf("_id" to "b", "rank" to null),
        mapOf("_id" to "c"),
        mapOf("_id" to "d", "rank" to null),
        mapOf("_id" to "e", "rank" to 1),
        mapOf("_id" to "f", "rank" to 3),
    )

private val NESTED =
    listOf(
        mapOf("_id" to "a", "pos" to mapOf("n" to 2)),
        mapOf("_id" to "b", "pos" to mapOf("n" to 1)),
        mapOf("_id" to "c"),
        mapOf("_id" to "d", "pos" to mapOf("n" to 3)),
        mapOf("_id" to "e", "pos" to mapOf("n" to 2)),
    )

private suspend fun seeded(
    documents: List<Map<String, Any?>>,
    block: suspend (ReactiveMongoTemplate) -> Unit,
) = SpringMongo.withTemplate { template ->
    documents.forEach { template.insert(org.bson.Document(it), "rows").awaitFirstOrNull() }
    block(template)
}

private suspend fun ReactiveMongoTemplate.walk(
    sort: Sort,
    size: Int,
    forward: Boolean = true,
): List<String> {
    val seen = mutableListOf<String>()
    var cursor: String? = null
    while (true) {
        val window = if (forward) MongoPage.first(size, cursor, sort = sort) else MongoPage.last(size, cursor, sort = sort)
        val page = findPage<Row>(window)
        if (forward) seen += page.data.map { it.id } else seen.addAll(0, page.data.map { it.id })
        if (forward) {
            if (!page.info.hasNextPage) break
            cursor = page.info.endCursor
        } else {
            if (!page.info.hasPreviousPage) break
            cursor = page.info.startCursor
        }
    }
    return seen
}

class NullAndNestedKeysetTest :
    FeatureSpec({

        feature("a sort field holding nulls and missing values").config(enabled = SpringMongo.available) {
            scenario("ascending, nulls first, every document exactly once") {
                seeded(RAW) { it.walk(Sort.by("rank"), 2) shouldBe listOf("b", "c", "d", "e", "a", "f") }
            }

            scenario("descending, nulls last, every document exactly once") {
                seeded(RAW) {
                    it.walk(Sort.by(Sort.Order.desc("rank"), Sort.Order.desc("_id")), 2) shouldBe
                        listOf("f", "a", "e", "d", "c", "b")
                }
            }

            scenario("paging backward from the end crosses the nulls too") {
                seeded(RAW) { it.walk(Sort.by("rank"), 2, forward = false) shouldBe listOf("b", "c", "d", "e", "a", "f") }
            }
        }

        feature("a sort on a nested field").config(enabled = SpringMongo.available) {
            scenario("the cursor reads the nested value, so no page is skipped or repeated") {
                seeded(NESTED) { it.walk(Sort.by("pos.n"), 2) shouldBe listOf("c", "b", "a", "e", "d") }
            }

            scenario("descending") {
                seeded(NESTED) {
                    it.walk(Sort.by(Sort.Order.desc("pos.n"), Sort.Order.desc("_id")), 2) shouldBe
                        listOf("d", "e", "a", "b", "c")
                }
            }
        }
    })
