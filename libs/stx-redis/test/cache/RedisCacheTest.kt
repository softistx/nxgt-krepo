package com.strange.redis.cache

import com.strange.redis.RedisTestServer
import com.strange.redis.codec.ValueCodec
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Serializable
private data class Session(
    val user: String,
    val roles: List<String> = emptyList(),
)

class RedisCacheTest :
    FeatureSpec({

        feature("a cached value").config(enabled = RedisTestServer.available) {
            scenario("it comes back as the type that was put in") {
                RedisTestServer.withRedis { redis ->
                    val sessions = redis.cache<Session>("sessions")
                    val session = Session("ada", listOf("admin"))

                    sessions.put("s1", session)

                    sessions.get("s1") shouldBe session
                    sessions.get("absent") shouldBe null
                    sessions.contains("s1") shouldBe true
                }
            }

            scenario("many at once is one round trip, and misses simply are not there") {
                RedisTestServer.withRedis { redis ->
                    val sessions = redis.cache<Session>("sessions")
                    sessions.putAll(mapOf("s1" to Session("ada"), "s2" to Session("grace")))

                    sessions.getAll(listOf("s1", "missing", "s2")) shouldBe
                        mapOf("s1" to Session("ada"), "s2" to Session("grace"))
                    sessions.getAll(emptyList()) shouldBe emptyMap()
                }
            }
        }

        feature("expiry").config(enabled = RedisTestServer.available) {
            scenario("the cache's default TTL is applied to what it writes") {
                RedisTestServer.withRedis { redis ->
                    val sessions = redis.cache<Session>("sessions", ttl = 30.minutes)
                    sessions.put("s1", Session("ada"))

                    sessions.expiresIn("s1")!! shouldBeLessThanOrEqualTo 30.minutes
                }
            }

            scenario("an entry written without one has no expiry, and neither has an absent key") {
                RedisTestServer.withRedis { redis ->
                    val sessions = redis.cache<Session>("sessions")
                    sessions.put("s1", Session("ada"))

                    sessions.expiresIn("s1") shouldBe null
                    sessions.expiresIn("absent") shouldBe null
                }
            }

            scenario("the value is gone once the TTL passes") {
                RedisTestServer.withRedis { redis ->
                    val sessions = redis.cache<Session>("sessions")
                    sessions.put("s1", Session("ada"), ttl = 150.milliseconds)

                    sessions.get("s1") shouldBe Session("ada")
                    delay(300)
                    sessions.get("s1") shouldBe null
                }
            }
        }

        feature("getOrLoad").config(enabled = RedisTestServer.available) {
            scenario("it loads once and then stops loading") {
                RedisTestServer.withRedis { redis ->
                    val sessions = redis.cache<Session>("sessions")
                    val loads = AtomicInteger()

                    repeat(3) {
                        sessions.getOrLoad("s1") { Session("ada").also { loads.incrementAndGet() } }
                    }

                    loads.get() shouldBe 1
                }
            }

            scenario("concurrent misses all load — it is a cache, not a lock") {
                RedisTestServer.withRedis { redis ->
                    val sessions = redis.cache<Session>("sessions")
                    val loads = AtomicInteger()

                    coroutineScope {
                        List(5) {
                            async {
                                sessions.getOrLoad("s1") {
                                    delay(50)
                                    Session("ada").also { loads.incrementAndGet() }
                                }
                            }
                        }.awaitAll()
                    }

                    loads.get() shouldBe 5
                }
            }
        }

        feature("invalidation").config(enabled = RedisTestServer.available) {
            scenario("one entry, and then all of them") {
                RedisTestServer.withRedis { redis ->
                    val sessions = redis.cache<Session>("sessions")
                    val flags = RedisCache(redis, "flags", ValueCodec.string)
                    sessions.putAll(mapOf("s1" to Session("ada"), "s2" to Session("grace")))
                    flags.put("beta", "on")

                    sessions.invalidate("s1") shouldBe true
                    sessions.invalidate("s1") shouldBe false

                    sessions.invalidateAll() shouldBe 1
                    sessions.get("s2") shouldBe null

                    // A cache clears its own prefix and nothing else's.
                    flags.get("beta") shouldBe "on"
                }
            }
        }
    })
