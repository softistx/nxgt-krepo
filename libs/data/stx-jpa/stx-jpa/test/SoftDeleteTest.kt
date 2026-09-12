package com.softistx.jpa

import com.softistx.jpa.entity.LegacyDollarNote
import com.softistx.jpa.entity.LegacyNote
import com.softistx.jpa.entity.SoftNote
import com.softistx.jpa.query.query
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * A delete that keeps the row.
 *
 * Both forms rewrite statements Hibernate would otherwise issue — the delete becomes an update, and
 * every read grows a restriction — which is exactly the kind of thing a different session
 * implementation can quietly not do. So it is measured on both sides: the entity is gone from the
 * session's view, and the row is still in the table when asked with SQL that knows nothing about the
 * mapping.
 */
class SoftDeleteTest :
    FeatureSpec({

        feature("@SoftDelete").config(enabled = JpaTestDatabase.available) {
            scenario("hides the row from every read while leaving it in the table") {
                JpaTestDatabase.withJpa(SoftNote::class) { jpa ->
                    jpa.transaction { session -> session.persist(SoftNote(1, "keep"), SoftNote(2, "drop")) }
                    jpa.transaction { session -> session.remove(session.get<SoftNote>(2L)) }

                    jpa.session { session ->
                        session.query<SoftNote>("from SoftNote").list().map { it.text } shouldBe listOf("keep")
                        session.find<SoftNote>(2L) shouldBe null
                    }

                    // The half a round-trip spec cannot see: the mapping hides the row, so only SQL
                    // that does not go through it can say whether the row is still there.
                    JpaTestDatabase.rows(jpa.config.schema!!, "soft_notes") shouldBe 2
                }
            }

            scenario("adds its own column, which is the whole mechanism") {
                JpaTestDatabase.withJpa(SoftNote::class) { jpa ->
                    JpaTestDatabase.columns(jpa.config.schema!!, "soft_notes") shouldContain "deleted"
                }
            }
        }

        // The hand-written form is handed to the driver untouched, so it carries neither of the two
        // things Hibernate's own SQL gets for free. Both halves fail, and both are measured, because
        // fixing the first only uncovers the second.
        feature("@SQLDelete, which does not survive the trip").config(enabled = JpaTestDatabase.available) {
            scenario("a JDBC '?' reaches the server literally, since there is no JDBC to read it") {
                val failure =
                    shouldThrowAny {
                        JpaTestDatabase.withJpa(LegacyNote::class) { jpa ->
                            jpa.transaction { session -> session.persist(LegacyNote(1, "drop")) }
                            jpa.transaction { session -> session.remove(session.get<LegacyNote>(1L)) }
                        }
                    }

                failure.message shouldContain "syntax error at end of input"
            }

            scenario("and correcting it to \$1 uncovers the name no schema will qualify") {
                // `JpaConfig.schema` is a runtime value and an annotation is a compile-time constant,
                // so nothing can close this. The README records the same trap for `nativeMutate`.
                val failure =
                    shouldThrowAny {
                        JpaTestDatabase.withJpa(LegacyDollarNote::class) { jpa ->
                            jpa.transaction { session -> session.persist(LegacyDollarNote(1, "drop")) }
                            jpa.transaction { session -> session.remove(session.get<LegacyDollarNote>(1L)) }
                        }
                    }

                failure.message shouldContain "\"dollar_notes\" does not exist"
            }
        }
    })
