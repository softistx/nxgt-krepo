package com.softistx.spring.data.mongo.filter

import com.softistx.spring.error.ApiException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.http.HttpStatus

private fun json(filter: String?) = filter.parseFilter().queryObject.toJson()

/**
 * The query as text, without asking BSON to encode the values.
 *
 * `toJson()` needs a codec for every value in the document, and `kotlin.time.Instant` and
 * `org.springframework.data.geo.Point` have none until a `MongoCustomConversions` is registered —
 * which is what `data/mongo/convert/` is for, and which a parser has no business requiring.
 */
private fun text(filter: String?) = filter.parseFilter().queryObject.toString()

class FiltersTest :
    StringSpec({
        "no filter is a query that excludes nothing" {
            null.parseFilter().queryObject.isEmpty() shouldBe true
            "".parseFilter().queryObject.isEmpty() shouldBe true
        }

        "clauses are combined with and by default" {
            val query = json("status:eq:PAID;total:gte:100")

            query shouldContain "\$and"
            query shouldContain "PAID"
            query shouldContain "\$gte"
        }

        "the or@ prefix switches the combinator" {
            json("or@status:eq:PAID;status:eq:SHIPPED") shouldContain "\$or"
        }

        "a single clause needs no combinator to be a query" {
            json("status:eq:PAID") shouldContain "PAID"
        }

        "a clause nobody can read is a failure, not a clause that is skipped" {
            // The one place lenience is the wrong instinct. Dropping a filter returns *more* rows
            // than the caller asked for: `status:eq:PIAD` would answer with the whole collection
            // rather than an empty page.
            val failure = shouldThrow<ApiException> { "status:PAID".parseFilter() }

            failure.status shouldBe HttpStatus.BAD_REQUEST
            failure.key shouldBe KEY_INVALID_FILTER
            failure.args shouldBe mapOf("filter" to "status:PAID")
        }

        "an operator this grammar does not have is not an operator" {
            // A filter grammar that passed operators through would let a caller write `\$where`,
            // which is JavaScript the server runs.
            shouldThrow<ApiException> { "status:where:1==1".parseFilter() }
            shouldThrow<ApiException> { "status:regex:.*".parseFilter() }
        }

        "a comparison against text that is not a number does not become zero" {
            // The version this came from coerced anything unparseable to 0.0, so `price:gte:cheap`
            // quietly became `price >= 0` and matched everything. A filter that means something
            // other than what it says is worse than one that fails.
            json("name:gte:m") shouldContain "\"m\""
        }

        "numbers and timestamps are compared as themselves" {
            json("total:gte:100") shouldContain "100"
            text("createdAt:from:2026-01-01T00:00:00Z") shouldContain "2026-01-01"
        }

        "a date operator given something that is not a date fails" {
            shouldThrow<ApiException> { "createdAt:before:yesterday".parseFilter() }
        }

        "exists takes a boolean and nothing else" {
            json("nickname:exists:true") shouldContain "\$exists"
            // `toBoolean()` answers false for "yes", "1" and every typo, so a caller asking for
            // documents that *have* a field would silently get the ones that do not.
            shouldThrow<ApiException> { "nickname:exists:yes".parseFilter() }
        }

        "like quotes what it was given" {
            json("name:like:C++") shouldContain "\\\\QC++\\\\E"
        }

        "the negated forms are not the positive ones" {
            json("name:!like:test") shouldContain "\$not"
            json("name:!in:a,b") shouldContain "\$nin"
        }

        "in splits on commas" {
            val query = json("status:in:PAID,SHIPPED")

            query shouldContain "PAID"
            query shouldContain "SHIPPED"
        }

        "a geo clause needs the right number of numbers" {
            text("location:near:2.35,48.85") shouldContain "\$near"
            shouldThrow<ApiException> { "location:near:2.35".parseFilter() }
            shouldThrow<ApiException> { "location:within:2.35,48.85".parseFilter() }
            text("location:within:2.35,48.85,10.0") shouldContain "\$geoWithin"
        }

        "a field cannot smuggle an operator in" {
            shouldThrow<ApiException> { "\$where:eq:1".parseFilter() }
        }
    })
