package com.softistx.spring.data.mongo.criteria

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.regex.Pattern

private data class Product(
    val name: String,
    val stock: Int,
)

class CriteriaTest :
    StringSpec({
        "a property names its own field" {
            // The whole reason the KProperty forms exist: rename `stock` and this stops compiling,
            // where the string form would keep compiling and quietly match nothing.
            (Product::stock gt 0).criteriaObject shouldBe ("stock" gt 0).criteriaObject
        }

        "the operators build what they say" {
            ("stock" eq 3).criteriaObject.toJson() shouldContain "\"stock\": 3"
            ("stock" ne 3).criteriaObject.toJson() shouldContain "\$ne"
            ("stock" lt 3).criteriaObject.toJson() shouldContain "\$lt"
            ("stock" gte 3).criteriaObject.toJson() shouldContain "\$gte"
            ("name" oneOf listOf("a", "b")).criteriaObject.toJson() shouldContain "\$in"
            ("name" noneOf listOf("a")).criteriaObject.toJson() shouldContain "\$nin"
            ("name" exists true).criteriaObject.toJson() shouldContain "\$exists"
            ("tags" size 2).criteriaObject.toJson() shouldContain "\$size"
        }

        "a substring search cannot hand the database a regular expression" {
            // Without quoting, a search box is a way to send Mongo a pattern. `(a+)+$` against a
            // long field is a request that does not come back — and it does not take a hostile user
            // to arrive, only someone searching for "C++".
            val pattern = ("name" containing "C++").criteriaObject.toJson()

            pattern shouldContain "\\\\QC++\\\\E"
        }

        "ignoring case is a flag, not a prefix smuggled into the pattern" {
            val criteria = "name" containingIgnoringCase "hammer"

            criteria.criteriaObject.toJson() shouldContain "\"i\""
        }

        "a compiled pattern keeps the flags it was given" {
            val criteria = "name" matching Pattern.compile("^h.*", Pattern.CASE_INSENSITIVE)

            criteria.criteriaObject.toJson() shouldContain "\"i\""
        }

        "two predicates on one field both survive" {
            // The reason `all` uses an explicit \$and rather than a chain: `where("price").gt(5)
            // .and("price").lt(10)` builds one key per field and silently keeps only the last.
            val json = all("price" gt 5, "price" lt 10).criteriaObject.toJson()

            json shouldContain "\$and"
            json shouldContain "\$gt"
            json shouldContain "\$lt"
        }

        "any and none are or and nor" {
            any("a" eq 1, "b" eq 2).criteriaObject.toJson() shouldContain "\$or"
            none("a" eq 1).criteriaObject.toJson() shouldContain "\$nor"
        }

        "a criterion becomes a query with nothing else on it" {
            ("stock" gt 0).query.queryObject.toJson() shouldContain "\$gt"
        }
    })
