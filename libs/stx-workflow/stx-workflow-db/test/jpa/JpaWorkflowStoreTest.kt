package com.strange.workflow.jpa

import com.strange.jpa.query.query
import com.strange.jpa.session.session
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.db.record
import com.strange.workflow.db.storeContract
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class JpaWorkflowStoreTest :
    FeatureSpec({
        storeContract("Postgres", JpaTestDatabase.available) { lease, block ->
            JpaTestDatabase.withJpa { jpa -> block(JpaWorkflowStore(jpa, lease = lease)) }
        }

        feature("what only the relational store does").config(enabled = JpaTestDatabase.available) {
            scenario("the record is stored as text an operator can read without a client library") {
                JpaTestDatabase.withJpa { jpa ->
                    JpaWorkflowStore(jpa).create(record("a"))

                    // Length.LONG32 rather than @Lob: on Postgres a @Lob String is an oid pointing
                    // into pg_largeobject, and `select record from stx_workflow_instance` then shows
                    // a number. A row per instance is only worth having if psql can read it.
                    val stored =
                        jpa.session { session ->
                            session
                                .query<String>("select r.record from WorkflowInstanceRow r where r.id = :id")
                                .parameter("id", "a")
                                .single()
                        }
                    stored.contains("\"workflow\":\"spec\"") shouldBe true
                    // The version is a column of its own, so a conditional update compares it
                    // without parsing this string first — the same reason it is not in the Redis
                    // store's document either.
                    stored.contains("\"version\"") shouldBe false
                }
            }

            scenario("purge deletes finished instances and leaves everything else alone") {
                JpaTestDatabase.withJpa { jpa ->
                    val store = JpaWorkflowStore(jpa)
                    store.create(record("done"))
                    store.create(record("stuck"))
                    store.create(record("running"))

                    val done = store.load("done")!!
                    store.save(done.copy(status = WorkflowStatus.Completed), done.version) shouldBe true
                    val stuck = store.load("stuck")!!
                    store.save(stuck.copy(status = WorkflowStatus.Failed), stuck.version) shouldBe true

                    // A cutoff in the future, so every finished instance is old enough and the
                    // scenario turns on which ones are candidates at all rather than on the clock.
                    store.purge(Clock.System.now() + 1.minutes) shouldBe 1
                    store.load("done") shouldBe null
                    // One that needs a person is exempt for the same reason it gets no TTL on
                    // Redis: deleting it removes the only description of what has to be fixed.
                    store.load("stuck")?.status shouldBe WorkflowStatus.Failed
                    store.load("running")?.status shouldBe WorkflowStatus.Running
                }
            }
        }
    })
