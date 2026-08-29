package com.strange.redis

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * The connection and the one decision it owns — what a key looks like. The namespace is what lets
 * these tests run against the same server as everything else in the workspace.
 */
class RedisTest :
    FeatureSpec({

        feature("a connection").config(enabled = RedisTestServer.available) {
            scenario("it answers, and Lettuce's own commands are right there") {
                RedisTestServer.withRedis { redis ->
                    redis.ping() shouldBe "PONG"

                    redis.commands.set(redis.key("greeting"), "hello")
                    redis.commands.get(redis.key("greeting")) shouldBe "hello"
                }
            }

            scenario("its keys are the ones the namespace names") {
                RedisTestServer.withRedis { redis ->
                    redis.key("cache", "user") shouldBe "${redis.namespace}:cache:user"
                }
            }
        }

        feature("a connection that is closed").config(enabled = RedisTestServer.available) {
            scenario("closing it twice is not an error") {
                // Not a hypothetical: Ktor's DI closes every AutoCloseable it hands out when the
                // application stops, and whoever built this one has its own claim to closing it.
                val redis = RedisTestServer.connect("stx-redis-close")

                redis.close()
                redis.close()

                shouldThrowAny { redis.ping() }
            }
        }

        feature("two namespaces on one server").config(enabled = RedisTestServer.available) {
            scenario("neither can see the other's keys") {
                RedisTestServer.withRedis { one ->
                    RedisTestServer.withRedis { two ->
                        one.commands.set(one.key("k"), "one")
                        two.commands.set(two.key("k"), "two")

                        one.commands.get(one.key("k")) shouldBe "one"
                        two.commands.get(two.key("k")) shouldBe "two"
                    }
                }
            }
        }
    })
