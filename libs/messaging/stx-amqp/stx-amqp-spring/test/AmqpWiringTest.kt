package com.softistx.amqp.spring

import com.softistx.amqp.Amqp
import com.softistx.testing.containers.rabbitContainer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * What this auto-configuration does and, mostly, does not do.
 *
 * A real broker for the second scenario, resolved the way every other spec in this repo resolves
 * one: `stx-testing` declares it, and asking for the endpoint is what starts a container — or
 * reuses the server `AMQP_TEST_URI` names. Gated on that rather than on an exported variable,
 * because a spec gated on a variable proves nothing on a machine where nobody exported it.
 */
class AmqpWiringTest :
    StringSpec({

        val broker = rabbitContainer()

        "nothing is opened until an application asks" {
            runner().run { context -> context.getBeanNamesForType(Amqp::class.java).size shouldBe 0 }
        }

        "enabling it opens a connection".config(enabled = broker.available) {
            runner()
                .withPropertyValues(
                    "stx.amqp.enabled=true",
                    "stx.amqp.uri=${broker.endpoint}",
                    "stx.amqp.connection-name=orders",
                ).run { context -> context.getBeanNamesForType(Amqp::class.java).size shouldBe 1 }
        }
    })

private fun runner() =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AmqpIntegrationAutoConfiguration::class.java))
