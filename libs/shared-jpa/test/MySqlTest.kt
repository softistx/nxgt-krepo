package com.strange.jpa

import com.strange.jpa.entity.Address
import com.strange.jpa.entity.Customer
import com.strange.jpa.entity.Thing
import com.strange.jpa.query.query
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The same library, against MySQL — because "supports three databases" is a claim about a driver
 * being on the classpath until something runs on one of them.
 *
 * Nothing in this spec is MySQL-flavoured: the entity, the session, the transaction and the query
 * are the ones `JpaTest` and `QueriesTest` use against Postgres. That is the point. Hibernate picks
 * the dialect from the connection, so the only thing that changes is the URI scheme.
 */
class MySqlTest :
    FeatureSpec({

        // MySQL is the one container here slow enough to lose a race with the rest of a full-module
        // run, so a skip needs to say why. `MySqlTestDatabase` prints it — kotest renders the reason
        // on `Enabled.disabled` as an empty string, so passing one through `enabledOrReasonIf` looks
        // like it works and does not.
        feature("a factory over MySQL").config(enabled = MySqlTestDatabase.available) {
            scenario("writes and reads back through a transaction") {
                MySqlTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.persist(Thing(1, "mysql")) }

                    jpa.session { it.get<Thing>(1) }.name shouldBe "mysql"
                }
            }

            scenario("runs the same HQL, with the parameters bound the same way") {
                MySqlTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Thing(1, "one"))
                        session.persist(Thing(2, "two"))
                    }

                    val found =
                        jpa.session { session ->
                            session
                                .query<Thing>("from Thing where name = :name")
                                .parameter("name", "two")
                                .list()
                        }

                    found.map { it.id } shouldContainExactly listOf(2L)
                    jpa.session { it.query<Thing>("from Thing").count() } shouldBe 2L
                }
            }
        }

        feature("a JSON column on MySQL").config(enabled = MySqlTestDatabase.available) {
            scenario("is a json column, and the same document goes in and comes back") {
                MySqlTestDatabase.withJpa(Customer::class) { jpa ->
                    val address = Address("Hauptstr 1", "Berlin", "DE", "second floor")
                    jpa.transaction { it.persist(Customer(1, address)) }

                    jpa.session { it.get<Customer>(1L) }.address shouldBe address

                    // `json`, where Postgres says `jsonb`. Worth asserting rather than assuming: the
                    // write path here is a Vert.x MySQL codec binding a JsonObject, which is a
                    // different piece of driver code from the one every other scenario exercises.
                    MySqlTestDatabase.columnType(
                        jpa.config.uri.substringAfterLast('/'),
                        "customers",
                        "address",
                    ) shouldBe "json"
                }
            }
        }
    })
