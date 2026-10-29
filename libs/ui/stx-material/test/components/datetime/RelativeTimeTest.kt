package com.softistx.material.components.datetime

import com.softistx.material.foundation.i18n.StxStrings
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

        feature("relativeTime in French") {
            val fr = StxStrings.Fr

            scenario("speaks French for every range") {
                relativeTime(now - 30.seconds, now, utc, fr) shouldBe "À l’instant"
                relativeTime(now - 3.minutes, now, utc, fr) shouldBe "il y a 3 min"
                relativeTime(now - 5.hours, now, utc, fr) shouldBe "il y a 5 h"
                relativeTime(now - 1.days, now, utc, fr) shouldBe fr.yesterday
                relativeTime(now - 3.days, now, utc, fr) shouldBe "il y a 3 jours"
                relativeTime(now + 8.minutes, now, utc, fr) shouldBe "dans 8 min"
            }

            scenario("a date past a week stays ISO whatever the language") {
                val then = now - 10.days
                relativeTime(then, now, utc, fr) shouldBe then.toLocalDateTime(utc).date.toString()
            }

            scenario("French treats 0 and 1 as singular") {
                fr.daysAgo(1) shouldBe "il y a 1 jour"
                fr.daysAgo(0) shouldBe "il y a 0 jour"
                fr.daysAgo(2) shouldBe "il y a 2 jours"
            }
        }
    })
