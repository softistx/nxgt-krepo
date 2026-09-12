package com.softistx.redis.spring

import com.softistx.redis.Redis
import com.softistx.testing.containers.redisContainer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * What this auto-configuration does and, mostly, does not do.
 *
 * The shape is the one every `stx.*` auto-configuration here shares — `@ConditionalOnClass`,
 * `@ConditionalOnProperty` with no `matchIfMissing`, `@ConditionalOnMissingBean` on the bean, built
 * through the library's own factory — so the questions are the same two asked of each: nothing
 * without the property, and the bean with it.
 *
 * A real server for the second, because `Redis.connect` is the library's own factory and a
 * connection that cannot be opened is not a connection this spec should claim was.
 */
class RedisWiringTest :
    StringSpec({

        val server = redisContainer()

        "nothing is opened until an application asks" {
            runner().run { context -> context.getBeanNamesForType(Redis::class.java).size shouldBe 0 }
        }

        "enabling it opens a connection".config(enabled = server.available) {
            runner()
                .withPropertyValues(
                    "stx.redis.enabled=true",
                    "stx.redis.uri=${server.endpoint}",
                    "stx.redis.namespace=orders",
                ).run { context -> context.getBean(Redis::class.java).namespace shouldBe "orders" }
        }
    })

private fun runner() =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisIntegrationAutoConfiguration::class.java))
