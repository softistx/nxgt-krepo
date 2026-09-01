package com.softistx.migrations.spring

import com.softistx.migrations.Migration
import com.softistx.migrations.MigrationContext
import com.softistx.migrations.MigrationFailedException
import com.softistx.migrations.MigrationHaltedException
import com.softistx.migrations.MigrationRunner
import com.softistx.migrations.MigrationStatus
import com.softistx.migrations.ledger.InMemoryLedger
import com.softistx.migrations.ledger.MigrationLedger
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * The gate, which is the whole reason this module is not two lines of application code.
 *
 * The spec that must not be skipped is *a failing migration aborts the context refresh*. The runner
 * this library replaces was a suspending `@EventListener(ApplicationReadyEvent)`, and
 * `SuspendingListenerTest` in `stx-spring-boot` pins that Spring does not wait for one — the port
 * opened while migrations were still running. A throw out of `afterPropertiesSet` is the difference,
 * and it is asserted here rather than described.
 *
 * No container: [InMemoryLedger] is a real ledger with a real conditional claim and a real lock, so
 * what these specs exercise is the wiring. That a ledger works against a server is
 * `stx-migrations-db`'s question, and `examples/spring-orders` runs the whole path end to end.
 */
class MigrationGateTest :
    FeatureSpec({

        val applied = mutableListOf<String>()

        fun migration(
            at: Long,
            body: suspend () -> Unit = { applied += "V$at" },
        ) = object : Migration<Unit> {
            override val version = at
            override val description = "V$at"

            override suspend fun migrate(context: Unit) = body()
        }

        fun runner(
            ledger: MigrationLedger,
            vararg migrations: Migration<Unit>,
        ) = MigrationRunner(ledger, migrations.toList(), MigrationContext { it(Unit) })

        fun contexts() =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MigrationAutoConfiguration::class.java))

        /** The startup failure, unwrapped far enough to name what actually went wrong. */
        fun failure(throwable: Throwable?): String =
            generateSequence(throwable) { it.cause }.joinToString("\n") { "${it::class.java.name}: ${it.message}" }

        beforeTest { applied.clear() }

        feature("the gate") {
            scenario("runs every MigrationRunner bean before the context is up") {
                contexts()
                    .withPropertyValues("stx.migrations.enabled=true")
                    .withBean(MigrationRunner::class.java, { runner(InMemoryLedger(), migration(1), migration(2)) })
                    .run { context ->
                        context.getBean(MigrationGate::class.java).ledger.map { it.status } shouldContainExactly
                            List(2) { MigrationStatus.APPLIED }
                        applied shouldContainExactly listOf("V1", "V2")
                    }
            }

            scenario("a failing migration aborts the refresh, so the application does not start") {
                contexts()
                    .withPropertyValues("stx.migrations.enabled=true")
                    .withBean(
                        MigrationRunner::class.java,
                        { runner(InMemoryLedger(), migration(1) { error("no such column") }) },
                    ).run { context ->
                        failure(context.startupFailure) shouldContain MigrationFailedException::class.java.name
                        failure(context.startupFailure) shouldContain "no such column"
                    }
            }

            scenario("and the startup after it refuses too, while the failure stands") {
                // One ledger, two contexts — the second is the redeploy that comes after the failure.
                val ledger = InMemoryLedger()

                contexts()
                    .withPropertyValues("stx.migrations.enabled=true")
                    .withBean(MigrationRunner::class.java, { runner(ledger, migration(1) { error("no") }) })
                    .run { it.startupFailure shouldBe it.startupFailure }
                applied.clear()

                contexts()
                    .withPropertyValues("stx.migrations.enabled=true")
                    .withBean(MigrationRunner::class.java, { runner(ledger, migration(1), migration(2)) })
                    .run { context ->
                        failure(context.startupFailure) shouldContain MigrationHaltedException::class.java.name
                        applied.shouldBeEmpty()
                    }
            }

            scenario("with no runner at all it is empty rather than a failure") {
                // Turning stx.migrations on before writing the first migration should not be an error.
                contexts().withPropertyValues("stx.migrations.enabled=true").run { context ->
                    context.getBean(MigrationGate::class.java).ledger.shouldBeEmpty()
                }
            }
        }

        feature("opting in") {
            scenario("nothing is registered until stx.migrations.enabled is true") {
                contexts()
                    .withBean(MigrationRunner::class.java, { runner(InMemoryLedger(), migration(1)) })
                    .run { context ->
                        context.containsBean("stxMigrationGate") shouldBe false
                        applied.shouldBeEmpty()
                    }
            }

            scenario("and enabled=false is the same as unset, because there is no matchIfMissing here") {
                contexts()
                    .withPropertyValues("stx.migrations.enabled=false")
                    .run { context -> context.containsBean("stxMigrationGate") shouldBe false }
            }
        }

        feature("naming a store without the connection it needs") {
            scenario("fails at startup rather than coming up quietly with no migrations") {
                // A context that started against a schema nobody made is the failure this library
                // exists to prevent, so an unanswerable `store` is an error and not a shrug.
                contexts()
                    .withPropertyValues("stx.migrations.enabled=true", "stx.migrations.store=sql")
                    .run { context -> failure(context.startupFailure) shouldContain "com.softistx.jpa.Jpa" }
            }

            scenario("the same for mongo") {
                contexts()
                    .withPropertyValues("stx.migrations.enabled=true", "stx.migrations.store=mongo")
                    .run { context -> failure(context.startupFailure) shouldContain "MongoDatabase" }
            }
        }
    })
