package com.softistx.spring.data.mongo.template

import com.softistx.spring.error.ApiException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import java.util.Date

class KeysetTest :
    StringSpec({
        "an ordering always ends in _id" {
            // Keyset pagination resumes from the last row's key, so the key has to be unique. Order
            // by `name` alone and every document sharing a name is a coin toss between being served
            // twice and being skipped.
            sortKeys(Sort.by("name"), null) shouldBe
                listOf(SortKey("name", ascending = true), SortKey(ID_FIELD, ascending = true))
        }

        "and does not add a second one when the caller already sorted by it" {
            sortKeys(Sort.by(Sort.Order.desc(ID_FIELD)), null) shouldBe listOf(SortKey(ID_FIELD, ascending = false))
        }

        "an unsorted query still has a key to page along" {
            sortKeys(Sort.unsorted(), null) shouldBe listOf(SortKey(ID_FIELD, ascending = true))
        }

        "the ordering flips when the page runs backward" {
            val keys = listOf(SortKey("name", ascending = true), SortKey(ID_FIELD, ascending = true))

            sortOf(keys, forward = true) shouldBe Sort.by(Sort.Order.asc("name"), Sort.Order.asc(ID_FIELD))
            sortOf(keys, forward = false) shouldBe Sort.by(Sort.Order.desc("name"), Sort.Order.desc(ID_FIELD))
        }

        "a cursor round-trips, keeping the BSON type it went out as" {
            // Extended JSON and not relaxed: a date that comes back as a string compares against
            // nothing, and the page after it would be empty rather than wrong — the harder failure
            // to notice.
            val keys = listOf(SortKey("createdAt", ascending = true), SortKey(ID_FIELD, ascending = true))
            val when0 = Date(1_788_027_695_000)
            val document = Document(mapOf("createdAt" to when0, ID_FIELD to "abc", "name" to "ignored"))

            val decoded = decodeCursor(encodeCursor(document, keys), keys)

            decoded[ID_FIELD] shouldBe "abc"
            decoded["createdAt"] shouldBe when0
            // Only the keys, so a cursor stays small and says nothing about the rest of the document.
            decoded.keys shouldBe setOf("createdAt", ID_FIELD)
        }

        "a cursor from a differently sorted query is refused" {
            // It would page along the wrong key and answer with rows that look plausible.
            val issued = encodeCursor(Document(ID_FIELD, "abc"), listOf(SortKey(ID_FIELD, ascending = true)))

            val failure =
                shouldThrow<ApiException> {
                    decodeCursor(issued, listOf(SortKey("name", ascending = true), SortKey(ID_FIELD, ascending = true)))
                }

            failure.status shouldBe HttpStatus.BAD_REQUEST
            failure.key shouldBe KEY_INVALID_PAGE
            failure.debugMessage!! shouldContain "different sort order"
        }

        "text that is not a cursor is refused rather than parsed into nonsense" {
            shouldThrow<ApiException> { decodeCursor("not-a-cursor", listOf(SortKey(ID_FIELD, ascending = true))) }
        }

        "resuming past one key is lexicographic, not a bare comparison" {
            // With more than one sort key, "after" means: the first key is greater, or it is equal
            // and the second is greater. A single \$gt on the leading key would skip every remaining
            // row that shares it.
            val keys = listOf(SortKey("name", ascending = true), SortKey(ID_FIELD, ascending = true))
            val cursor = Document(mapOf("name" to "hammer", ID_FIELD to "abc"))

            val json = keysetCriteria(cursor, keys, forward = true).criteriaObject.toJson()

            json shouldContain "\$or"
            json shouldContain "\$gt"
        }

        "a backward page compares the other way" {
            val keys = listOf(SortKey(ID_FIELD, ascending = true))

            keysetCriteria(Document(ID_FIELD, "abc"), keys, forward = false).criteriaObject.toJson() shouldContain "\$lt"
        }

        "a key the document has no value for compares as null rather than blowing up" {
            val keys = listOf(SortKey("nickname", ascending = true), SortKey(ID_FIELD, ascending = true))

            val json = keysetCriteria(Document(ID_FIELD, "abc"), keys, forward = true).criteriaObject.toJson()

            json shouldContain "null"
        }
    })
