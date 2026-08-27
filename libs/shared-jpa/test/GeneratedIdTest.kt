package com.strange.jpa

import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Which `@GeneratedValue` strategies actually work here, asked of the database rather than assumed. */
@OptIn(ExperimentalUuidApi::class)
class GeneratedIdTest :
    FeatureSpec({

        feature("an identifier the database assigns").config(enabled = JpaTestDatabase.available) {
            scenario("AUTO") {
                JpaTestDatabase.withJpa(AutoId::class) { jpa ->
                    val entity = AutoId(name = "auto")
                    jpa.transaction { it.persist(entity) }

                    entity.id shouldNotBe 0L
                    jpa.session { it.find<AutoId>(entity.id) }?.name shouldBe "auto"
                }
            }

            scenario("SEQUENCE") {
                JpaTestDatabase.withJpa(SequenceId::class) { jpa ->
                    val entity = SequenceId(name = "sequence")
                    jpa.transaction { it.persist(entity) }

                    entity.id shouldNotBe 0L
                }
            }

            scenario("IDENTITY") {
                JpaTestDatabase.withJpa(IdentityId::class) { jpa ->
                    val entity = IdentityId(name = "identity")
                    jpa.transaction { it.persist(entity) }

                    entity.id shouldNotBe 0L
                }
            }

            scenario("UUID, as java.util.UUID") {
                JpaTestDatabase.withJpa(JavaUuidId::class) { jpa ->
                    val entity = JavaUuidId(name = "java uuid")
                    jpa.transaction { it.persist(entity) }

                    entity.id shouldNotBe null
                }
            }

            scenario("and a kotlin.uuid.Uuid identifier is refused before it can write a blob") {
                // Not a limitation this module chose. A converter is not allowed on an @Id, and
                // without one Hibernate serializes the unmapped type: the primary key comes out
                // `bytea`, everything succeeds, and no other client of that database can read the
                // table. Refusing at connect is the last moment that is still preventable.
                val failure =
                    shouldThrow<IllegalStateException> {
                        JpaTestDatabase.withJpa(UuidId::class) { }
                    }

                failure.message shouldContain "UuidId.id"
                failure.message shouldContain "java.util.UUID"
            }
        }
    })
