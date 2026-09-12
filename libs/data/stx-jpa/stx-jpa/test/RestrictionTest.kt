package com.softistx.jpa

import com.softistx.jpa.entity.Isbn
import com.softistx.jpa.entity.Lease
import com.softistx.jpa.entity.Postmark
import com.softistx.jpa.entity.Quota
import com.softistx.jpa.query.findAll
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * `@Filter`, `@Immutable` and `@NaturalId` — three claims about the session rather than about a
 * column, measured against a session that is reactive.
 *
 * `@Filter` is the one with stakes: it is how multi-tenancy is usually done, and
 * `docs/jpa-mapping.md` named it as the most interesting thing nothing exercised. Half the answer
 * came from reading `Stage.java` — `enableFilter`, `disableFilter` and `getEnabledFilter` are all
 * there, at lines 1492-1508 — and the other half is here.
 */
class RestrictionTest :
    FeatureSpec({

        feature("a filter that is not enabled").config(enabled = JpaTestDatabase.available) {
            scenario("shows every tenant's rows, and says nothing about it") {
                JpaTestDatabase.withJpa(Lease::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Lease(1, "acme", "one"))
                        session.persist(Lease(2, "globex", "two"))
                    }

                    // The trap, not the feature. A `@Filter` is inert until somebody turns it on, so
                    // the mapping that looks like it enforces isolation enforces nothing, and the
                    // failure is a query returning too much rather than an error.
                    jpa.session { session -> session.findAll<Lease>().size } shouldBe 2
                }
            }
        }

        feature("a filter enabled on the session").config(enabled = JpaTestDatabase.available) {
            scenario("restricts the query, through the reactive session") {
                JpaTestDatabase.withJpa(Lease::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Lease(1, "acme", "one"))
                        session.persist(Lease(2, "globex", "two"))
                    }

                    jpa.session { session ->
                        session.raw.enableFilter("byTenant").setParameter("tenant", "acme")

                        val visible = session.findAll<Lease>()

                        visible.size shouldBe 1
                        visible.single().tenant shouldBe "acme"
                    }
                }
            }

            scenario("does not outlive the session that enabled it") {
                JpaTestDatabase.withJpa(Lease::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Lease(1, "acme", "one"))
                        session.persist(Lease(2, "globex", "two"))
                    }
                    jpa.session { session ->
                        session.raw.enableFilter("byTenant").setParameter("tenant", "acme")
                        session.findAll<Lease>().size
                    }

                    // A filter is session state, and `session { }` opens a new one every time — so
                    // enabling it once at startup is not a thing that can be done.
                    jpa.session { session -> session.findAll<Lease>().size } shouldBe 2
                }
            }
        }

        feature("a filter defined autoEnabled").config(enabled = JpaTestDatabase.available) {
            scenario("still needs its parameter, and fails without one") {
                JpaTestDatabase.withJpa(Quota::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Quota(1, "ada", 10))
                        session.persist(Quota(2, "grace", 20))
                    }

                    // `autoEnabled` turns the filter on for every session; it does not supply the
                    // parameter, and a filter with an unset parameter is an error rather than a
                    // no-op. So it removes the "somebody forgot" failure and replaces it with a
                    // loud one, which is the trade worth knowing about.
                    shouldThrowAny { jpa.session { session -> session.findAll<Quota>() } }
                }
            }

            scenario("restricts every query once the parameter is set") {
                JpaTestDatabase.withJpa(Quota::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Quota(1, "ada", 10))
                        session.persist(Quota(2, "grace", 20))
                    }

                    jpa.session { session ->
                        session.raw.getEnabledFilter("byOwner").setParameter("owner", "ada")

                        session.findAll<Quota>().single().owner shouldBe "ada"
                    }
                }
            }
        }

        feature("an immutable entity").config(enabled = JpaTestDatabase.available) {
            scenario("drops a change instead of refusing it") {
                JpaTestDatabase.withJpa(Postmark::class) { jpa ->
                    jpa.transaction { session -> session.persist(Postmark(1, "first")) }

                    // Not an exception. `@Immutable` takes the entity out of dirty checking, so the
                    // assignment succeeds, the transaction commits, and the row is unchanged — which
                    // is the failure mode to know about, because nothing anywhere says no.
                    jpa.transaction { session -> session.get<Postmark>(1L).stamp = "second" }

                    jpa.session { session -> session.get<Postmark>(1L).stamp } shouldBe "first"
                }
            }
        }

        feature("a natural id").config(enabled = JpaTestDatabase.available) {
            scenario("is a unique constraint, and nothing else here") {
                JpaTestDatabase.withJpa(Isbn::class) { jpa ->
                    jpa.transaction { session -> session.persist(Isbn(1, "978-0", "Dune")) }

                    // What survives the reactive port: the column is unique.
                    shouldThrowAny {
                        jpa.transaction { session -> session.persist(Isbn(2, "978-0", "Emma")) }
                    }

                    // And what does not: `Stage.Session` has no `byNaturalId`, so the lookup the
                    // annotation exists for — with its own cache in blocking Hibernate — is an
                    // ordinary query here. `@Column(unique = true)` would have bought the same
                    // thing.
                    jpa.session { session ->
                        session
                            .query<Isbn>("from Isbn where code = :code")
                            .parameter("code", "978-0")
                            .single()
                            .title
                    } shouldBe "Dune"
                }
            }
        }
    })
