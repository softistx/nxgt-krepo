package com.strange.jpa.query

import com.strange.jpa.JpaNoResultException
import com.strange.jpa.JpaNonUniqueResultException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Thing
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** The HQL surface: what it selects, what it refuses, and what it says when it refuses. */
class QueriesTest :
    FeatureSpec({

        suspend fun JpaSession.seed(vararg names: String) {
            names.forEachIndexed { index, name -> persist(Thing((index + 1).toLong(), name)) }
        }

        feature("a selection").config(enabled = JpaTestDatabase.available) {
            scenario("binds its parameters instead of interpolating them") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.seed("one", "two", "three") }

                    val found =
                        jpa.session { session ->
                            session
                                .query<Thing>("from Thing where name = :name")
                                .parameter("name", "two")
                                .list()
                        }

                    found.map { it.name } shouldContainExactly listOf("two")
                }
            }

            scenario("pages with limit and offset, in the order it asked for") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.seed("a", "b", "c", "d") }

                    val page =
                        jpa.session { session ->
                            session
                                .query<Thing>("from Thing order by id")
                                .limit(2)
                                .offset(1)
                                .list()
                        }

                    page.map { it.name } shouldContainExactly listOf("b", "c")
                }
            }

            scenario("counts every match, not the page") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.seed("a", "b", "c", "d") }

                    jpa.session { it.query<Thing>("from Thing").limit(2).count() } shouldBe 4L
                }
            }

            scenario("projects to something that is not an entity") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.seed("a", "b") }

                    jpa.session { it.query<String>("select name from Thing order by id").list() } shouldContainExactly
                        listOf("a", "b")
                }
            }
            scenario("projects to a primitive type") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.seed("a", "b") }

                    // Worth pinning, because it reads like it should not work: a reified
                    // `Long::class.java` is `long.class`, not `Long.class`, and that is what the
                    // builders hand Hibernate. It boxes it. Nothing here needs a `javaObjectType`,
                    // and this scenario is what would notice if that stopped being true.
                    jpa.session { it.query<Long>("select count(id) from Thing").single() } shouldBe 2L
                    jpa.session { it.nativeQuery<Long>("select count(*) from ${jpa.config.schema}.things").single() } shouldBe 2L
                }
            }

            scenario("answers null for a first that matched nothing") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.session { it.query<Thing>("from Thing").first() }.shouldBeNull()
                }
            }
        }

        feature("single, which is the one that throws").config(enabled = JpaTestDatabase.available) {
            scenario("names the query when nothing matched") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    val failure =
                        shouldThrow<JpaNoResultException> {
                            jpa.session { it.query<Thing>("from Thing where name = 'nobody'").single() }
                        }

                    failure.message shouldContain "from Thing where name = 'nobody'"
                }
            }

            scenario("refuses more than one row rather than taking the first") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.seed("same", "same") }

                    shouldThrow<JpaNonUniqueResultException> {
                        jpa.session { it.query<Thing>("from Thing where name = :name").parameter("name", "same").single() }
                    }
                }
            }

            scenario("answers null from singleOrNull, and still refuses two") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.session { it.query<Thing>("from Thing").singleOrNull() }.shouldBeNull()

                    jpa.transaction { it.seed("same", "same") }

                    shouldThrow<JpaNonUniqueResultException> {
                        jpa.session { it.query<Thing>("from Thing").singleOrNull() }
                    }
                }
            }
        }

        feature("SQL, where HQL runs out").config(enabled = JpaTestDatabase.available) {
            scenario("selects through the same builder, against a table it has to name in full") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.seed("one", "two") }

                    // Qualified, and that is the rule rather than this harness being awkward:
                    // Hibernate qualifies the table it renders from HQL, and sends native SQL as
                    // written. Unqualified here, this is `relation "things" does not exist`.
                    val table = "${jpa.config.schema}.things"

                    val found =
                        jpa.session { session ->
                            session
                                .nativeQuery<String>("select name from $table where name like :like order by id")
                                .parameter("like", "t%")
                                .list()
                        }

                    found shouldContainExactly listOf("two")
                }
            }

            scenario("mutates, and answers with the rows it touched") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.seed("a", "b") }

                    val table = "${jpa.config.schema}.things"

                    jpa.transaction { it.nativeMutate("update $table set name = 'x'").execute() } shouldBe 2
                }
            }
        }

        feature("a bulk mutation").config(enabled = JpaTestDatabase.available) {
            scenario("answers with the number of rows it touched") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.seed("keep", "drop", "drop") }

                    val deleted =
                        jpa.transaction { session ->
                            session.mutate("delete from Thing where name = :name").parameter("name", "drop").execute()
                        }

                    deleted shouldBe 2
                    jpa.session { it.query<Thing>("from Thing").count() } shouldBe 1L
                }
            }
        }
    })
