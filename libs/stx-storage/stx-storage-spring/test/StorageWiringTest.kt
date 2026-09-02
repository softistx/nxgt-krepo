package com.softistx.storage.spring

import com.softistx.storage.ObjectStorage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * What this auto-configuration does and, mostly, does not do.
 *
 * No server, and none needed: the MinIO client is lazy about its first request, which is what lets
 * a wiring spec assert the wiring — and is also why a wrong credential surfaces on first use rather
 * than at startup. The third scenario is the one that matters most here.
 */
class StorageWiringTest :
    StringSpec({

        "nothing is opened until an application asks" {
            runner().run { context -> context.getBeanNamesForType(ObjectStorage::class.java).size shouldBe 0 }
        }

        "enabling it opens a client" {
            runner()
                .withPropertyValues(
                    "stx.storage.enabled=true",
                    "stx.storage.endpoint=$UNREACHABLE_HTTP",
                    "stx.storage.access-key=who",
                    "stx.storage.secret-key=cares",
                ).run { context -> context.getBeanNamesForType(ObjectStorage::class.java).size shouldBe 1 }
        }

        "a missing credential names itself, and none of them has a default" {
            // A credential with a default is a credential in source control.
            runner()
                .withPropertyValues("stx.storage.enabled=true", "stx.storage.endpoint=$UNREACHABLE_HTTP")
                .run { context -> context.failure() shouldContain "stx.storage.access-key" }

            with(StorageIntegrationProperties()) {
                accessKey shouldBe null
                secretKey shouldBe null
            }
        }
    })

/**
 * An HTTP address nothing listens on.
 *
 * `localhost:9000` is the workspace's MinIO, which every property in this spec used to name. The
 * credentials here are junk, so a real store would have refused them — but a wiring spec that points
 * at a port somebody else is serving is one library change away from doing something on it.
 */
private const val UNREACHABLE_HTTP = "http://127.0.0.1:1"

private fun runner() =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(StorageIntegrationAutoConfiguration::class.java))

/** The message of whatever stopped the context starting, with its causes — the `require` is a cause. */
private fun AssertableApplicationContext.failure(): String =
    generateSequence(startupFailure) { it.cause }
        .mapNotNull { it.message }
        .joinToString("\n")
