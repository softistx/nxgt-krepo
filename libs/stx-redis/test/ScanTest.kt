package com.strange.redis

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList

/**
 * Walking and deleting by pattern — and the one thing about both that has to be true.
 *
 * A pattern here is relative to the connection's namespace, so the pattern that reads like "every
 * key", `"*"`, is every key *this connection owns*. That is not a nicety: `deleteKeys` was once
 * given the pattern verbatim, and the bare `"*"` a caller naturally reaches for would have emptied
 * database 15 out from under every other spec in this repo. The second scenario is that near miss,
 * written down.
 */
class ScanTest :
    FeatureSpec({

        feature("scanning").config(enabled = RedisTestServer.available) {
            scenario("it walks what the pattern names, under this connection's namespace") {
                RedisTestServer.withRedis { redis ->
                    redis.commands.set(redis.key("user", "1"), "a")
                    redis.commands.set(redis.key("user", "2"), "b")
                    redis.commands.set(redis.key("order", "1"), "c")

                    redis.scanKeys("user:*").toList() shouldContainExactlyInAnyOrder
                        listOf(redis.key("user", "1"), redis.key("user", "2"))

                    redis.scanKeys().toList().size shouldBe 3
                }
            }
        }

        feature("deleting by pattern").config(enabled = RedisTestServer.available) {
            scenario("the widest pattern there is still stops at the namespace") {
                RedisTestServer.withRedis { redis ->
                    // A key belonging to somebody else on the same database — another spec's
                    // namespace, another service, whatever db 15 is holding at the time.
                    val outsider = "stx-redis-test-outsider:untouched"
                    redis.commands.set(outsider, "keep me")
                    redis.commands.set(redis.key("mine", "1"), "a")
                    redis.commands.set(redis.key("mine", "2"), "b")

                    redis.deleteKeys() shouldBe 2

                    redis.commands.get(outsider) shouldBe "keep me"
                    redis.commands.del(outsider)
                }
            }

            scenario("it deletes only the match, and answers with how many") {
                RedisTestServer.withRedis { redis ->
                    redis.commands.set(redis.key("tmp", "1"), "a")
                    redis.commands.set(redis.key("tmp", "2"), "b")
                    redis.commands.set(redis.key("keep"), "c")

                    redis.deleteKeys("tmp:*") shouldBe 2

                    redis.scanKeys().toList() shouldContainExactlyInAnyOrder listOf(redis.key("keep"))
                }
            }

            scenario("a pattern that matches nothing deletes nothing") {
                RedisTestServer.withRedis { redis ->
                    redis.deleteKeys("absent:*") shouldBe 0
                }
            }
        }
    })
