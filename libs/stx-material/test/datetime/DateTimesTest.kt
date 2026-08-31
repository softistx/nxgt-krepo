package com.strange.material.datetime

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class DateTimesTest :
    FeatureSpec({
        feature("epoch round trip") {
            scenario("a date survives millis and back, at UTC start of day") {
                val date = LocalDate(2026, 8, 31)
                date.toEpochMillis().toLocalDate() shouldBe date
            }
        }

        feature("display") {
            scenario("formats a date as ISO") {
                LocalDate(2026, 8, 31).format() shouldBe "2026-08-31"
            }

            scenario("formats a time as HH:mm") {
                LocalTime(9, 5).format() shouldBe "09:05"
            }
        }
    })
