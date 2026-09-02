package com.softistx.jpa.spring

import com.softistx.jpa.Jpa
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * What this auto-configuration does and, mostly, does not do.
 *
 * No database: nothing here connects. Hibernate Reactive's pool opens its first connection when a
 * session is asked for, which is what lets a wiring spec assert the wiring — and is also why a
 * wrong password surfaces on first use rather than at startup.
 */
class JpaWiringTest :
    StringSpec({

        "nothing is built until an application asks" {
            runner().run { context -> context.getBeanNamesForType(Jpa::class.java).size shouldBe 0 }
        }

        "enabling it without packages says so" {
            // Naming no packages would build a session factory that maps nothing, and the first
            // query would fail with an unrelated message about an unknown entity.
            runner()
                .withPropertyValues("stx.jpa.enabled=true")
                .run { context -> context.failure() shouldContain "stx.jpa.packages" }
        }
    })

private fun runner() =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JpaIntegrationAutoConfiguration::class.java))

/** The message of whatever stopped the context starting, with its causes — the `require` is a cause. */
private fun AssertableApplicationContext.failure(): String =
    generateSequence(startupFailure) { it.cause }
        .mapNotNull { it.message }
        .joinToString("\n")
