package com.strange.spring.data.mongo.convert

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

class InstantConvertersTest :
    StringSpec({
        "a kotlin Instant survives the round trip, to the millisecond" {
            val written = Instant.parse("2026-08-29T18:21:34.686Z")

            val date = InstantToDateConverter().convert(written)

            DateToInstantConverter().convert(date) shouldBe written
        }

        "and loses what BSON cannot hold" {
            // BSON has one date type and it holds milliseconds. Worth knowing before building a
            // cursor or an equality check out of a stored timestamp; irrelevant for a createdAt
            // somebody displays. Storing a string would keep the nanoseconds and lose range queries
            // and index ordering, which is the worse trade.
            val written = Instant.parse("2026-08-29T18:21:34.686320906Z")

            val readBack = DateToInstantConverter().convert(InstantToDateConverter().convert(written))

            readBack shouldBe Instant.parse("2026-08-29T18:21:34.686Z")
        }

        "the conversions bean carries both directions" {
            val conversions = stxMongoConversions()

            conversions.hasCustomWriteTarget(Instant::class.java) shouldBe true
        }
    })
