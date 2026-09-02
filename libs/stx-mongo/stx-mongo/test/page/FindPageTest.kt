package com.softistx.mongo.page

import com.softistx.common.page.PageInfo
import com.softistx.mongo.InvalidPaginationException
import com.softistx.mongo.MongoTestCluster
import com.softistx.mongo.Note
import com.softistx.mongo.query.insertAll
import com.softistx.mongo.withNotes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.bson.BsonDocument

private fun json(source: String) = Json.parseToJsonElement(source) as JsonObject

/**
 * Ids and tags are deliberately out of step — insertion order is not id order, and three tags are
 * shared by two documents each. A keyset that forgot its `_id` tiebreak passes a test where the
 * sort field is unique and loses documents here.
 */
private val NOTES =
    listOf(
        Note("d", "fourth", tag = "a"),
        Note("a", "first", tag = "a"),
        Note("c", "third", tag = "b"),
        Note("b", "second", tag = "b"),
        Note("f", "sixth", tag = "c"),
        Note("e", "fifth", tag = "c"),
    )

/** By (tag, _id) — the ordering `{"tag": 1}` really means once the tiebreak is added. */
private val BY_TAG = listOf("a", "d", "b", "c", "e", "f")

class FindPageTest :
    FeatureSpec({

        feature("paging forward").config(enabled = MongoTestCluster.available) {
            scenario("each page resumes where the last one ended") {
                withNotes { notes ->
                    notes.insertAll(NOTES)

                    val firstPage = notes.findPage(PaginationOptions.first(2))
                    firstPage.data.map { it.id } shouldBe listOf("a", "b")
                    firstPage.info.hasNextPage shouldBe true
                    firstPage.info.hasPreviousPage shouldBe false

                    val secondPage = notes.findPage(PaginationOptions.first(2, firstPage.info.endCursor))
                    secondPage.data.map { it.id } shouldBe listOf("c", "d")
                    secondPage.info.hasPreviousPage shouldBe true

                    val lastPage = notes.findPage(PaginationOptions.first(2, secondPage.info.endCursor))
                    lastPage.data.map { it.id } shouldBe listOf("e", "f")
                    lastPage.info.hasNextPage shouldBe false
                }
            }

            scenario("a full walk under a sort with ties visits every document exactly once") {
                withNotes { notes ->
                    notes.insertAll(NOTES)

                    val seen = mutableListOf<String>()
                    var options = PaginationOptions(first = 2, sort = json("""{"tag": 1}"""))
                    while (true) {
                        val page = notes.findPage(options)
                        seen += page.data.map { it.id }
                        if (!page.info.hasNextPage) break
                        options = options.copy(cursor = page.info.endCursor)
                    }

                    seen shouldBe BY_TAG
                }
            }
        }

        feature("paging backward").config(enabled = MongoTestCluster.available) {
            scenario("the page reads in the collection's order, not the query's") {
                withNotes { notes ->
                    notes.insertAll(NOTES)

                    val end = notes.findPage(PaginationOptions.last(2))
                    end.data.map { it.id } shouldBe listOf("e", "f")
                    end.info.hasPreviousPage shouldBe true
                    end.info.hasNextPage shouldBe false

                    val before = notes.findPage(PaginationOptions.last(2, end.info.startCursor))
                    before.data.map { it.id } shouldBe listOf("c", "d")
                    before.info.hasNextPage shouldBe true
                }
            }
        }

        feature("the filter").config(enabled = MongoTestCluster.available) {
            scenario("it narrows the result set the page is cut from") {
                withNotes { notes ->
                    notes.insertAll(NOTES)

                    val page = notes.findPage(PaginationOptions(first = 10, filter = json("""{"tag": "b"}""")))

                    page.data.map { it.id } shouldBe listOf("b", "c")
                    page.info.hasNextPage shouldBe false
                }
            }
        }

        feature("no page size at all").config(enabled = MongoTestCluster.available) {
            scenario("everything comes back, with cursors and nothing on either side") {
                withNotes { notes ->
                    notes.insertAll(NOTES)

                    val page = notes.findPage(PaginationOptions())

                    page.data.map { it.id } shouldBe listOf("a", "b", "c", "d", "e", "f")
                    page.info.hasNextPage shouldBe false
                    page.info.hasPreviousPage shouldBe false
                    decodeCursor(page.info.startCursor!!, sortKeys(null)) shouldBe BsonDocument.parse("""{"_id": "a"}""")
                    decodeCursor(page.info.endCursor!!, sortKeys(null)) shouldBe BsonDocument.parse("""{"_id": "f"}""")
                }
            }

            scenario("an empty collection is an empty page, not a failure") {
                withNotes { notes ->
                    val page = notes.findPage(PaginationOptions.first(10))

                    page.data shouldBe emptyList()
                    page.info shouldBe PageInfo()
                }
            }
        }

        feature("a cursor that does not belong to this query").config(enabled = MongoTestCluster.available) {
            scenario("it is refused rather than paged from the wrong key") {
                withNotes { notes ->
                    notes.insertAll(NOTES)

                    val sorted = notes.findPage(PaginationOptions(first = 2, sort = json("""{"tag": 1}""")))

                    shouldThrow<InvalidPaginationException> {
                        notes.findPage(PaginationOptions.first(2, sorted.info.endCursor))
                    }
                }
            }
        }
    })
