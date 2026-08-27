package com.strange.redis.codec

import com.strange.redis.RedisTestServer
import com.strange.redis.RedisValueException
import com.strange.redis.cache.RedisCache
import com.strange.redis.cache.cache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class Profile(
    val user: String,
)

/**
 * The connection owns one `Json`, and these are the specs that hold it to that: a cache built by
 * `redis.cache<T>()` must serialize through *that* instance and not through a default one it made
 * for itself, or configuring Redis once would be a claim the module does not keep.
 */
class RedisJsonTest :
    FeatureSpec({

        val withUnknownField = """{"user":"ada","device":"phone"}"""

        feature("the connection's Json").config(enabled = RedisTestServer.available) {
            scenario("its leniency reaches the cache: a value from an older version still reads") {
                RedisTestServer.withRedis { redis ->
                    val profiles = redis.cache<Profile>("profiles")
                    redis.commands.set(profiles.key("p1"), withUnknownField)

                    profiles.get("p1") shouldBe Profile("ada")
                }
            }

            scenario("and so does its strictness, when the connection was given a strict one") {
                RedisTestServer.withRedis(json = Json) { redis ->
                    val profiles = redis.cache<Profile>("profiles")
                    redis.commands.set(profiles.key("p1"), withUnknownField)

                    val failure = shouldThrow<RedisValueException> { profiles.get("p1") }

                    failure.message shouldContain "Profile"
                }
            }
        }

        feature("the serializer constructor").config(enabled = RedisTestServer.available) {
            scenario("it reads what the reified factory wrote — one encoding, two ways in") {
                RedisTestServer.withRedis { redis ->
                    redis.cache<Profile>("profiles").put("p1", Profile("ada"))

                    // The form a call site takes when its T cannot be reified.
                    RedisCache(redis, "profiles", Profile.serializer()).get("p1") shouldBe Profile("ada")
                }
            }
        }
    })
