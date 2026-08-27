package com.strange.jpa

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
    })
