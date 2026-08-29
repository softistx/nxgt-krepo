package com.strange.redis

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * Key naming is the one thing every layer here shares, and the one thing a shared Redis instance
 * depends on being right — a namespace that does not lead means another application's keys.
 */
class KeysTest :
    FeatureSpec({

        feature("a key under a namespace") {
            scenario("the namespace leads and colons join") {
                redisKey("app", "cache", "user", "42") shouldBe "app:cache:user:42"
            }

            scenario("empty parts are dropped, not turned into empty segments") {
                redisKey("app", "", "user") shouldBe "app:user"
                redisKey("", "cache", "user") shouldBe "cache:user"
                redisKey("") shouldBe ""
            }
        }
    })
