package com.softistx.material.datetime

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RelativeTimeTest :
    FeatureSpec({
        val now = Instant.fromEpochSeconds(1_777_766_400)
        val utc = TimeZone.UTC

        feature("relativeTime") {
            scenario("under a minute is just now") {
                relativeTime(now - 30.seconds, now, utc) shouldBe "Just now"
            }

            scenario("under an hour is minutes") {
                relativeTime(now - 3.minutes, now, utc) shouldBe "3 min ago"
            }

            scenario("under a day is hours") {
                relativeTime(now - 5.hours, now, utc) shouldBe "5 h ago"
            }

            scenario("the previous calendar day is yesterday") {
                relativeTime(now - 1.days, now, utc) shouldBe "Yesterday"
            }

            scenario("a week or more is the ISO date") {
                val then = now - 10.days
                relativeTime(then, now, utc) shouldBe then.toLocalDateTime(utc).date.toString()
            }

            scenario("a future minute spells in, not ago") {
                relativeTime(now + 8.minutes, now, utc) shouldBe "in 8 min"
            }
        }
    })
