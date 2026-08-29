package com.strange.spring.data.mongo.migration

import com.strange.spring.data.mongo.template.SpringMongo
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.context.support.GenericApplicationContext

/** Records the order units ran in, so a spec can assert one ran and another did not. */
private val applied = ThreadLocal.withInitial { mutableListOf<String>() }

@MigrationUnit("adds the currency column")
private class V1Currencies : Migration {
    override suspend fun migrate() {
        applied.get() += "V1"
    }
}

private class V2Backfill : Migration {
    override suspend fun migrate() {
        applied.get() += "V2"
    }
}

private class V3Explodes : Migration {
    override suspend fun migrate(): Unit = error("no")
}

/** Same order as [V1Currencies], and so the same code. */
private class V1Rival : Migration {
    override suspend fun migrate() {
        applied.get() += "rival"
    }
}

/** A migration bean the naming rule does not accept. */
private class Backfill : Migration {
    override suspend fun migrate() {
        applied.get() += "unnamed"
    }
}

/**
 * A fresh database and a [Migrations] over it — a factory rather than one runner, because a startup
 * is one context and some of these scenarios are about the second startup.
 */
private suspend fun migrations(block: suspend (Migrations) -> Unit) =
    SpringMongo.withTemplate { template ->
        applied.get().clear()
        val contexts = mutableListOf<GenericApplicationContext>()
        try {
            block(Migrations(MigrationStore(template), contexts::add))
        } finally {
            contexts.forEach { it.close() }
        }
    }

private class Migrations(
    val store: MigrationStore,
    private val opened: (GenericApplicationContext) -> Unit,
) {
    /** A runner whose context holds [units] and nothing else — one application's worth. */
    fun of(vararg units: Migration): MigrationRunner {
        val context =
            GenericApplicationContext().apply {
                units.forEachIndexed { index, unit -> beanFactory.registerSingleton("unit$index", unit) }
                refresh()
            }
        opened(context)
        return MigrationRunner(context, store, "V")
    }
}

class MigrationRunnerTest :
    FeatureSpec({

        feature("applying migrations").config(enabled = SpringMongo.available) {
            scenario("they run in the order their names give, not the order the context lists them") {
                migrations {
                    val entries = it.of(V2Backfill(), V1Currencies()).run()

                    applied.get() shouldContainExactly listOf("V1", "V2")
                    entries.map { it.code } shouldContainExactly listOf("V1", "V2")
                    entries.map { it.status } shouldContainExactly
                        listOf(MigrationStatus.APPLIED, MigrationStatus.APPLIED)
                }
            }

            scenario("an applied migration does not run twice") {
                migrations {
                    val runner = it.of(V1Currencies())
                    runner.run()
                    applied.get().clear()

                    runner.run().single().status shouldBe MigrationStatus.APPLIED
                    applied.get() shouldContainExactly emptyList()
                }
            }

            scenario("the description comes from the annotation, and falls back to the name") {
                migrations { m ->
                    m.of(V1Currencies(), V2Backfill()).run().map { it.description } shouldContainExactly
                        listOf("adds the currency column", "Backfill")
                }
            }

            scenario("nothing to run writes nothing") {
                migrations {
                    it.of().run() shouldContainExactly emptyList()
                    it.store.find("V1") shouldBe null
                }
            }
        }

        feature("refusing to run").config(enabled = SpringMongo.available) {
            scenario("a failure is recorded and stops everything after it") {
                // Migrations are written against the state the previous one left, so continuing past
                // a failure applies a change to a database that is not in the shape it expects.
                migrations { m ->
                    val entries = m.of(V3Explodes(), V1Currencies()).run().associateBy { it.code }

                    applied.get() shouldContainExactly listOf("V1")
                    entries.getValue("V1").status shouldBe MigrationStatus.APPLIED
                    entries.getValue("V3").status shouldBe MigrationStatus.FAILED
                    entries.getValue("V3").failure shouldBe "no"
                }
            }

            scenario("a later startup runs nothing while a failure stands") {
                migrations { m ->
                    m.of(V3Explodes()).run()
                    applied.get().clear()

                    // A second unit, added by a deploy that came after the failure.
                    val second = m.of(V3Explodes(), V1Currencies())
                    second.run().single { it.code == "V1" }.status shouldBe MigrationStatus.PENDING
                    applied.get() shouldContainExactly emptyList()
                }
            }

            scenario("two units at the same order abort the run") {
                // Their codes would collide, so one would be recorded as the other and never run.
                // Which one is arbitrary, and an arbitrary migration order is worse than none.
                migrations {
                    it.of(V1Currencies(), V1Rival()).run() shouldContainExactly emptyList()
                    applied.get() shouldContainExactly emptyList()
                    it.store.find("V1") shouldBe null
                }
            }

            scenario("a Migration bean the naming rule rejects does not run") {
                migrations { m ->
                    m.of(Backfill(), V1Currencies()).run().map { it.code } shouldContainExactly listOf("V1")
                    applied.get() shouldContainExactly listOf("V1")
                }
            }
        }
    })
