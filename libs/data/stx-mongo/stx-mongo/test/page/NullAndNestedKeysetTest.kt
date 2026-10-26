package com.softistx.mongo.page

import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.softistx.mongo.MongoTestCluster
import com.softistx.mongo.collection
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.bson.BsonDocument

private fun json(source: String) = Json.parseToJsonElement(source) as JsonObject

@Serializable
private data class Row(
    @SerialName("_id") val id: String,
    val rank: Int? = null,
)

/** Ranks mix a stored null (`b`, `d`), a missing field (`c`) and values, so every kind of null is walked. */
private val RAW =
    listOf(
        """{"_id": "a", "rank": 2}""",
        """{"_id": "b", "rank": null}""",
        """{"_id": "c"}""",
        """{"_id": "d", "rank": null}""",
        """{"_id": "e", "rank": 1}""",
        """{"_id": "f", "rank": 3}""",
    )

/** Nested: `{"pos": {"n": ...}}`, with one document lacking the parent altogether. */
private val NESTED =
    listOf(
        """{"_id": "a", "pos": {"n": 2}}""",
        """{"_id": "b", "pos": {"n": 1}}""",
        """{"_id": "c"}""",
        """{"_id": "d", "pos": {"n": 3}}""",
        """{"_id": "e", "pos": {"n": 2}}""",
    )

private suspend fun walk(
    collection: MongoCollection<Row>,
    first: PaginationOptions,
): List<String> {
    val seen = mutableListOf<String>()
    var options = first
    while (true) {
        val page = collection.findPage(options)
        seen += page.data.map { it.id }
        if (!page.info.hasNextPage) break
        options = options.copy(cursor = page.info.endCursor)
    }
    return seen
}

private suspend fun seeded(
    documents: List<String>,
    block: suspend (MongoCollection<Row>) -> Unit,
) = MongoTestCluster.withDatabase { _, database ->
    database.getCollection<BsonDocument>("rows").insertMany(documents.map { BsonDocument.parse(it) })
    block(database.collection<Row>("rows"))
}

class NullAndNestedKeysetTest :
    FeatureSpec({

        feature("a sort field holding nulls and missing values").config(enabled = MongoTestCluster.available) {
            scenario("ascending, nulls first, every document exactly once") {
                seeded(RAW) { rows ->
                    walk(rows, PaginationOptions(first = 2, sort = json("""{"rank": 1}"""))) shouldBe
                        listOf("b", "c", "d", "e", "a", "f")
                }
            }

            scenario("descending, nulls last, every document exactly once") {
                seeded(RAW) { rows ->
                    walk(rows, PaginationOptions(first = 2, sort = json("""{"rank": -1, "_id": -1}"""))) shouldBe
                        listOf("f", "a", "e", "d", "c", "b")
                }
            }

            scenario("paging backward from the end crosses the nulls too") {
                seeded(RAW) { rows ->
                    val sort = json("""{"rank": 1}""")
                    val seen = mutableListOf<String>()
                    var options = PaginationOptions(last = 2, sort = sort)
                    while (true) {
                        val page = rows.findPage(options)
                        seen.addAll(0, page.data.map { it.id })
                        if (!page.info.hasPreviousPage) break
                        options = options.copy(cursor = page.info.startCursor)
                    }
                    seen shouldBe listOf("b", "c", "d", "e", "a", "f")
                }
            }
        }

        feature("a sort on a nested field").config(enabled = MongoTestCluster.available) {
            scenario("the cursor reads the nested value, so no page is skipped or repeated") {
                seeded(NESTED) { rows ->
                    walk(rows, PaginationOptions(first = 2, sort = json("""{"pos.n": 1}"""))) shouldBe
                        listOf("c", "b", "a", "e", "d")
                }
            }

            scenario("descending") {
                seeded(NESTED) { rows ->
                    walk(rows, PaginationOptions(first = 2, sort = json("""{"pos.n": -1, "_id": -1}"""))) shouldBe
                        listOf("d", "e", "a", "b", "c")
                }
            }
        }
    })
