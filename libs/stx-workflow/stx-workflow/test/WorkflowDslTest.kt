package com.softistx.workflow

import com.softistx.workflow.dsl.branch
import com.softistx.workflow.dsl.compensate
import com.softistx.workflow.dsl.outcome
import com.softistx.workflow.dsl.parallel
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Ledger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class WorkflowDslTest :
    FeatureSpec({
        feature("what a declaration refuses") {
            scenario("a workflow with no nodes") {
                shouldThrow<IllegalArgumentException> { workflow<Ledger>("empty") { } }
                    .message!! shouldContain "declares no nodes"
            }

            scenario("two nodes with the same name, because the journal is keyed on them") {
                shouldThrow<IllegalArgumentException> {
                    workflow<Ledger>("twice") {
                        step("charge") { context }
                        step("charge") { context }
                    }
                }.message!! shouldContain "declares 'charge' twice"
            }

            scenario("a step given two compensations") {
                shouldThrow<IllegalArgumentException> {
                    workflow<Ledger>("two-undos") {
                        step("charge") { context } compensate { } compensate { }
                    }
                }.message!! shouldContain "already has a compensation"
            }

            scenario("a fan-out with no merge, because nothing would say what its legs did") {
                shouldThrow<IllegalStateException> {
                    workflow<Ledger>("unmerged") {
                        parallel("provision") { branch(outcome<String>("charge")) { "c-1" } }
                    }
                }.message!! shouldContain "has no merge"
            }

            scenario("a branch whose 'otherwise' is not last") {
                shouldThrow<IllegalArgumentException> {
                    workflow<Ledger>("misordered") {
                        branch("pick") {
                            otherwise { step("fallback") { context } }
                            on("express", { it.express }) { step("fast") { context } }
                        }
                    }
                }.message!! shouldContain "'otherwise' must come last"
            }
        }

        feature("names inside a container") {
            scenario("a leg and a step may share a short name, because a leg's name is qualified") {
                val flow =
                    workflow<Ledger>("qualified") {
                        step("charge") { context }
                        parallel("provision") {
                            branch(outcome<String>("charge")) { "c-1" }
                            merge { out -> context.copy(chargeId = out[outcome<String>("charge")]) }
                        }
                    }
                flow.name shouldBe "qualified"
            }
        }
    })
