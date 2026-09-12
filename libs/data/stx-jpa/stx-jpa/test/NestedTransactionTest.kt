package com.softistx.jpa

import com.softistx.jpa.entity.SharedRow
import com.softistx.jpa.entity.Thing
import com.softistx.jpa.query.findAll
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * What a `jpa.transaction { }` inside another one actually opens, which is not a second one.
 *
 * This is here because it cost a wrong measurement to find. A spec for `@DynamicUpdate` was written
 * as *"load the row, let somebody else change a different column, then flush"* — with the somebody
 * else being a nested `jpa.transaction { }` — and it failed by passing: both the dynamic and the
 * static mapping came out identical, because there had only ever been one session and one
 * transaction. `SpreadTest` now uses two factories, and this is the fact that sent it there.
 *
 * **`Stage.SessionFactory.withTransaction` joins the transaction in scope.** That is Hibernate
 * Reactive's own behaviour and a reasonable one — it is what makes a service method that opens a
 * transaction safe to call from another that already has — but it means `jpa.transaction { }` is not
 * a way to get a second connection, and a test or a job that assumed otherwise is measuring nothing.
 */
class NestedTransactionTest :
    FeatureSpec({

        feature("a transaction opened inside another").config(enabled = JpaTestDatabase.available) {
            scenario("is the same session, not a second one") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { outer ->
                        jpa.transaction { inner -> inner.raw === outer.raw } shouldBe true
                    }
                }
            }

            scenario("commits once, with the outer block, and rolls back with it too") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    // The inner block returned cleanly. Nothing was committed by it, because there
                    // was nothing of its own to commit — so the outer throwing takes the inner's
                    // write with it.
                    shouldThrowAny {
                        jpa.transaction { outer ->
                            outer.persist(Thing(1, "written by the outer"))
                            jpa.transaction { inner -> inner.persist(Thing(2, "written by the inner")) }
                            error("the outer block fails after the inner one returned")
                        }
                    }

                    jpa.session { session -> session.findAll<Thing>().size } shouldBe 0
                }
            }
        }

        feature("a second factory").config(enabled = JpaTestDatabase.available) {
            scenario("is what actually gives two transactions on one schema") {
                JpaTestDatabase.withJpa(SharedRow::class) { jpa ->
                    jpa.transaction { session -> session.persist(SharedRow(1, "draft", "nobody")) }

                    jpa.withSecondFactory(SharedRow::class) { other ->
                        jpa.session { one ->
                            other.session { two -> (one.raw === two.raw) shouldBe false }
                        }

                        // And it really is a second transaction: this one commits while the outer
                        // session is still open, and the outer sees nothing of it until it reloads.
                        other.transaction { it.get<SharedRow>(1L).summary = "committed elsewhere" }
                    }

                    jpa.session { session -> session.get<SharedRow>(1L).summary } shouldBe "committed elsewhere"
                }
            }
        }
    })
