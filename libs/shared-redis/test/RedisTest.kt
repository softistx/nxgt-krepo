package com.strange.redis

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
