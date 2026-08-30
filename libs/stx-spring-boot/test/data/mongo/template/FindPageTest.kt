package com.strange.spring.data.mongo.template

import com.strange.spring.data.mongo.criteria.eq
import com.strange.spring.data.mongo.criteria.query
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document("notes")
private data class Note(
    @Id val id: String,
    @Field("t") val tag: String,
)

/**
 * Ids and tags are deliberately out of step — insertion order is not id order, and three tags are
 * each shared by two notes. A keyset that forgot its `_id` tiebreak passes against a unique sort
 * field and loses documents here.
 *
 * `tag` is stored as `t`, which is the other thing these pin: a cursor built from the *property*
 * name would read nothing back, and every page after the first would come up empty.
 */
private val NOTES =
    listOf(
        Note("d", "a"),
        Note("a", "a"),
        Note("c", "b"),
        Note("b", "b"),
        Note("f", "c"),
        Note("e", "c"),
    )

/** By (tag, _id) — what "sort by tag" really means once the tiebreak is added. */
private val BY_TAG = listOf("a", "d", "b", "c", "e", "f")

private val BY_TAG_SORT = Sort.by("tag")

/** A template on its own database, seeded with [NOTES], dropped when the block returns. */
private suspend fun seeded(block: suspend (ReactiveMongoTemplate) -> Unit) =
    SpringMongo.withTemplate { template ->
        NOTES.forEach { template.insert(it).awaitFirstOrNull() }
        block(template)
    }

class FindPageTest :
    FeatureSpec({

        feature("paging forward").config(enabled = SpringMongo.available) {
            scenario("each page resumes exactly where the last one ended") {
                seeded { template ->
                    val first = template.findPage<Note>(MongoPage.first(2, sort = BY_TAG_SORT))
                    first.data.map { it.id } shouldBe BY_TAG.take(2)
                    first.info.hasNextPage shouldBe true
                    first.info.hasPreviousPage shouldBe false

                    val second =
                        template.findPage<Note>(MongoPage.first(2, cursor = first.info.endCursor, sort = BY_TAG_SORT))
                    second.data.map { it.id } shouldBe BY_TAG.drop(2).take(2)
                    // Known without asking: the caller arrived here from somewhere.
                    second.info.hasPreviousPage shouldBe true

                    val third =
                        template.findPage<Note>(MongoPage.first(2, cursor = second.info.endCursor, sort = BY_TAG_SORT))
                    third.data.map { it.id } shouldBe BY_TAG.drop(4)
                    third.info.hasNextPage shouldBe false
                }
            }

            scenario("a tie on the sort field loses nobody") {
                // The `_id` tiebreak, tested where it matters: every tag is shared by two notes.
                seeded { template ->
                    val seen = mutableListOf<String>()
                    var cursor: String? = null
                    repeat(6) {
                        val page = template.findPage<Note>(MongoPage.first(1, cursor = cursor, sort = BY_TAG_SORT))
                        seen += page.data.map { note -> note.id }
                        cursor = page.info.endCursor
                    }

                    seen shouldBe BY_TAG
                }
            }

            scenario("the cursor is built from the stored field name, not the property") {
                // `tag` is `t` in the document. A cursor naming the property would read nothing back
                // and the second page would be empty.
                seeded { template ->
                    val first = template.findPage<Note>(MongoPage.first(2, sort = BY_TAG_SORT))
                    val second =
                        template.findPage<Note>(MongoPage.first(2, cursor = first.info.endCursor, sort = BY_TAG_SORT))

                    second.data.map { it.id } shouldBe BY_TAG.drop(2).take(2)
                }
            }

            scenario("no page size means the whole result set, with no next page") {
                seeded { template ->
                    val page = template.findPage<Note>(MongoPage(sort = BY_TAG_SORT))

                    page.data.map { it.id } shouldBe BY_TAG
                    page.info.hasNextPage shouldBe false
                }
            }
        }

        feature("paging backward").config(enabled = SpringMongo.available) {
            scenario("the last page comes back in reading order") {
                seeded { template ->
                    val page = template.findPage<Note>(MongoPage.last(2, sort = BY_TAG_SORT))

                    // Read in reverse by the database, handed back the way the caller reads it.
                    page.data.map { it.id } shouldBe BY_TAG.takeLast(2)
                    page.info.hasPreviousPage shouldBe true
                    page.info.hasNextPage shouldBe false
                }
            }

            scenario("and walks back to the start") {
                seeded { template ->
                    val last = template.findPage<Note>(MongoPage.last(3, sort = BY_TAG_SORT))
                    val before =
                        template.findPage<Note>(MongoPage.last(3, cursor = last.info.startCursor, sort = BY_TAG_SORT))

                    before.data.map { it.id } shouldBe BY_TAG.take(3)
                    before.info.hasPreviousPage shouldBe false
                }
            }
        }

        feature("the query travels with the window").config(enabled = SpringMongo.available) {
            scenario("a filter narrows the page and the cursors stay inside it") {
                seeded { template ->
                    val page = template.findPage<Note>(MongoPage.first(10, query = ("t" eq "b").query))

                    page.data.map { it.id } shouldBe listOf("b", "c")
                    page.info.hasNextPage shouldBe false
                }
            }

            scenario("a filter matching nothing is an empty page with no cursors") {
                seeded { template ->
                    val page = template.findPage<Note>(MongoPage.first(10, query = ("t" eq "zzz").query))

                    page.data shouldBe emptyList()
                    page.info.startCursor shouldBe null
                    page.info.hasNextPage shouldBe false
                }
            }
        }
    })
