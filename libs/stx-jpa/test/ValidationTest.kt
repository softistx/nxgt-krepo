package com.softistx.jpa

import com.softistx.jpa.entity.Validated
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jakarta.validation.ConstraintViolationException

/**
 * That Bean Validation actually runs here.
 *
 * Worth asking rather than assuming: Hibernate ORM applies constraints through event listeners on
 * insert and update, and Hibernate Reactive replaces the listeners it fires. Either the reactive
 * path keeps them or it does not, and the difference between the two is a library that silently
 * stores whatever it is given.
 */
class ValidationTest :
    FeatureSpec({

        feature("a constrained entity").config(enabled = JpaTestDatabase.available) {
            scenario("is refused before it reaches the database") {
                JpaTestDatabase.withJpa(Validated::class) { jpa ->
                    shouldThrow<ConstraintViolationException> {
                        jpa.transaction { it.persist(Validated(1, "x")) }
                    }

                    jpa.session { it.find<Validated>(1) }.shouldBeNull()
                }
            }

            scenario("is refused for a null the column would have taken") {
                JpaTestDatabase.withJpa(Validated::class) { jpa ->
                    shouldThrow<ConstraintViolationException> {
                        jpa.transaction { it.persist(Validated(2, null)) }
                    }
                }
            }

            scenario("is stored when it satisfies them") {
                JpaTestDatabase.withJpa(Validated::class) { jpa ->
                    jpa.transaction { it.persist(Validated(3, "acceptable")) }

                    jpa.session { it.find<Validated>(3) }?.name shouldBe "acceptable"
                }
            }

            scenario("and its constraints reach the schema, not only the check") {
                JpaTestDatabase.withJpa(Validated::class) { jpa ->
                    // @Size(max = 10) is a DDL fact as well as a runtime one: Hibernate exports the
                    // column at the length the constraint allows rather than the default 255.
                    JpaTestDatabase.columnMaxLength(jpa.config.schema!!, "validated", "name") shouldBe 10
                }
            }
        }
    })
