package com.softistx.redis.codec

import com.softistx.redis.RedisValueException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable

@Serializable
private data class Session(
    val user: String,
    val roles: List<String> = emptyList(),
)

/**
 * What ends up in the keyspace is not this module's private business — `redis-cli`, RedisInsight and
 * whatever else reads the same key all have an opinion. Hence a string codec that stores a string.
 */
class ValueCodecTest :
    FeatureSpec({

        feature("a string value") {
            scenario("it is stored as itself, not as a quoted JSON string") {
                ValueCodec.string.encode("hello") shouldBe "hello"
                ValueCodec.string.decode("hello") shouldBe "hello"
            }
        }

        feature("a serializable value") {
            scenario("it round-trips as JSON") {
                val codec = ValueCodec.json<Session>()
                val session = Session("ada", listOf("admin"))

                codec.encode(session) shouldContain """"user":"ada""""
                codec.decode(codec.encode(session)) shouldBe session
            }

            scenario("a value written by something else names the type it could not be read as") {
                val failure = shouldThrow<RedisValueException> { ValueCodec.json<Session>().decode("""{"nope": 1}""") }

                failure.message shouldContain "Session"
            }
        }
    })
