package com.softistx.spring.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SortOrderTest :
    StringSpec({
        "a clause parses into a property and a direction" {
            "name:ASC".parseSort() shouldBe listOf(SortOrder("name", descending = false))
            "name:DESC".parseSort() shouldBe listOf(SortOrder("name", descending = true))
        }

        "ASC really is ascending" {
            // The regression this spec exists for. The original compared the parsed direction with
            // `===`, so every clause fell through to descending — and nothing caught it, because a
            // wrongly ordered result is still a result. `first()` here would pass either way; the
            // `descending` flag is what has to be read.
            "name:ASC".parseSort().single().descending shouldBe false
        }

        "several clauses keep the order they were written in" {
            "name:ASC,createdAt:DESC".parseSort() shouldBe
                listOf(SortOrder("name"), SortOrder("createdAt", descending = true))
        }

        "a nested property is a property" {
            "metadata.createdDate:DESC".parseSort() shouldBe listOf(SortOrder("metadata.createdDate", descending = true))
        }

        "the direction is read whatever its case" {
            "name:asc,price:desc".parseSort() shouldBe listOf(SortOrder("name"), SortOrder("price", descending = true))
        }

        "no parameter is no ordering" {
            null.parseSort() shouldBe emptyList()
            "".parseSort() shouldBe emptyList()
        }

        "a clause nobody can parse loses only itself" {
            "name:ASC,;;;,price:SIDEWAYS,stock:DESC".parseSort() shouldBe
                listOf(SortOrder("name"), SortOrder("stock", descending = true))
        }

        "a property cannot smuggle an operator into a query" {
            // This reaches a store as a field name. A `$where` or a `$ne` in that position is an
            // injection, and the narrow pattern is what refuses it.
            """${'$'}where:ASC""".parseSort() shouldBe emptyList()
            "field\$ne:ASC".parseSort() shouldBe emptyList()
        }
    })
