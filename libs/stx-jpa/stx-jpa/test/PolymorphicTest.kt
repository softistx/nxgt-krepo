package com.softistx.jpa

import com.softistx.jpa.entity.Attachment
import com.softistx.jpa.entity.Buyer
import com.softistx.jpa.entity.Thing
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.future.await
import org.hibernate.LazyInitializationException

/**
 * `@Any` — the last mapping `docs/jpa-mapping.md` left named and unmeasured, and the one that came
 * back least like the prediction.
 *
 * The guess was that a target whose *table* is a column could not be a proxy, so `@Any` would have to
 * load eagerly or not at all. It is a proxy: the discriminator is read with the owner, which is
 * exactly enough to know which class to proxy. What is deferred is the row, and that lands under the
 * same rule as every other association here — `fetch` it, or it throws.
 */
class PolymorphicTest :
    FeatureSpec({

        feature("an @Any association").config(enabled = JpaTestDatabase.available) {
            scenario("stores the target's type beside its identifier, with no foreign key") {
                JpaTestDatabase.withJpa(Attachment::class, Buyer::class, Thing::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Buyer(1, "Ada"))
                        session.persist(Thing(1, "widget"))
                        session.persist(Attachment(1, "about the buyer", session.get<Buyer>(1L)))
                        session.persist(Attachment(2, "about the thing", session.get<Thing>(1L)))
                    }

                    // Two columns and no constraint: nothing can reference `target_id`, because what
                    // it points at depends on the value beside it. That is the cost of the mapping,
                    // and it is paid in the schema rather than in the code.
                    JpaTestDatabase.columns(jpa.config.schema!!, "attachments") shouldBe
                        listOf("body", "id", "target_id", "target_type")
                }
            }

            scenario("gives back a proxy of the class the discriminator names") {
                JpaTestDatabase.withJpa(Attachment::class, Buyer::class, Thing::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Buyer(1, "Ada"))
                        session.persist(Thing(1, "widget"))
                        session.persist(Attachment(1, "about the buyer", session.get<Buyer>(1L)))
                        session.persist(Attachment(2, "about the thing", session.get<Thing>(1L)))
                    }

                    jpa.session { session ->
                        // Reading the property does not throw and does not go to the database. The
                        // discriminator came with the owner, so Hibernate already knows the class —
                        // which is why an association with no fixed table can still be lazy.
                        session.get<Attachment>(1L).target!!::class.java.name shouldContain "Buyer"
                        session.get<Attachment>(2L).target!!::class.java.name shouldContain "Thing"
                    }
                }
            }

            scenario("throws on the first property read, inside the session as much as outside") {
                JpaTestDatabase.withJpa(Attachment::class, Buyer::class, Thing::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Buyer(1, "Ada"))
                        session.persist(Attachment(1, "about the buyer", session.get<Buyer>(1L)))
                    }

                    // Inside. `HR000037` is refused here as it is everywhere in this library: there
                    // is no thread to park on the second select.
                    jpa.session { session ->
                        shouldThrow<LazyInitializationException> { (session.get<Attachment>(1L).target as Buyer).name }
                    }

                    // And outside, where the proxy outlives the session that made it. The trap is
                    // that `attachment.target` itself is silent — a caller who only passes the
                    // reference around finds out at whatever line first touches it.
                    val detached = jpa.session { it.get<Attachment>(1L) }
                    shouldThrowAny { (detached.target as Buyer).name }
                }
            }

            scenario("resolves through fetch, which is the only thing that resolves it") {
                JpaTestDatabase.withJpa(Attachment::class, Buyer::class, Thing::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Buyer(1, "Ada"))
                        session.persist(Attachment(1, "about the buyer", session.get<Buyer>(1L)))
                    }

                    jpa.session { session ->
                        val attachment = session.get<Attachment>(1L)

                        // `fetch` and not `fetchEach`, and not an entity graph: both of those plan a
                        // join, and there is no table to join to until the row has been read. So an
                        // `@Any` is always a second statement — it cannot be folded into the first.
                        (session.raw.fetch(attachment.target).await() as Buyer).name shouldBe "Ada"
                    }
                }
            }

            scenario("needs every class its discriminator names to be a mapped entity") {
                // `Thing` is left out of the factory, though `@AnyDiscriminatorValue` names it.
                //
                // The interesting half is *when* that is noticed. Not at `Jpa.connect`, which
                // succeeds — the discriminator converter is built lazily, from the whole list at
                // once, the first time anything touches the association. So the mapping is wrong
                // from the start and the first statement is where it says so, on a row that has
                // nothing to do with the missing class.
                shouldThrowAny {
                    JpaTestDatabase.withJpa(Attachment::class, Buyer::class) { jpa ->
                        jpa.transaction { session ->
                            session.persist(Buyer(1, "Ada"))
                            session.persist(Attachment(1, "about the buyer", session.get<Buyer>(1L)))
                        }
                    }
                }.message shouldContain "Thing"
            }
        }
    })
