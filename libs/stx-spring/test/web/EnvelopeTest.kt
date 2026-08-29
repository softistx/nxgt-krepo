package com.strange.spring.web

import com.strange.common.page.Page
import com.strange.common.page.PageInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class EnvelopeTest :
    StringSpec({
        "a page splits into rows and cursors" {
            val page = Page(listOf("a", "b"), PageInfo(startCursor = "1", endCursor = "2", hasNextPage = true))

            val body = page.response()

            body.data shouldBe listOf("a", "b")
            body.metadata shouldBe page.info
        }

        "the cursors survive the envelope on the wire" {
            // The reason Page exists rather than a bare List: a client that has lost the cursors
            // cannot ask for the next page, and this is the layer where that would happen.
            val page = Page(listOf("a"), PageInfo(endCursor = "cursor-2", hasNextPage = true))

            val json =
                Json.encodeToString(
                    Response.serializer(ListSerializer(String.serializer()), PageInfo.serializer()),
                    page.response(),
                )

            json shouldContain "\"endCursor\":\"cursor-2\""
            json shouldContain "\"hasNextPage\":true"
        }

        "a value with no metadata is still a body" {
            // `Response<D, Nothing>` is the interesting case: kotlinx has to serialize a type
            // argument that has no values, which it can only do because the field is always null.
            val json = Json.encodeToString(Response.serializer(String.serializer(), String.serializer()), "x".response())

            json shouldContain "\"data\":\"x\""
        }

        "links come through when a route sends them" {
            "x".response(mapOf("self" to "/products/7")).links shouldBe mapOf("self" to "/products/7")
        }
    })
