package com.strange.spring.data.mongo.template

import com.strange.spring.error.ApiException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

class MongoPageTest :
    StringSpec({
        "asking for both directions is a contradiction" {
            val failure = shouldThrow<ApiException> { MongoPage(first = 10, last = 10) }

            // A 400 and not a family of its own: every way a page request goes wrong is a client
            // sending something it should not have, and it arrives translated like everything else.
            failure.status shouldBe HttpStatus.BAD_REQUEST
            failure.key shouldBe KEY_INVALID_PAGE
        }

        "a page of nothing is not a page" {
            shouldThrow<ApiException> { MongoPage(first = 0) }
            shouldThrow<ApiException> { MongoPage(last = -1) }
        }

        "neither direction means the whole result set" {
            MongoPage().limit shouldBe null
            MongoPage().forward shouldBe true
        }

        "only an explicit last pages backward" {
            MongoPage.first(20).forward shouldBe true
            MongoPage.last(20).forward shouldBe false
            MongoPage.last(20).limit shouldBe 20
        }
    })
