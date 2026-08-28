package com.strange.jpa.json

import com.strange.jpa.JpaDocumentException
import com.strange.jpa.JpaMappingException
import com.strange.jpa.JpaSerializerException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Address
import com.strange.jpa.entity.Basket
import com.strange.jpa.entity.Box
import com.strange.jpa.entity.Bundle
import com.strange.jpa.entity.Crate
import com.strange.jpa.entity.Customer
import com.strange.jpa.entity.Label
import com.strange.jpa.entity.Plain
import com.strange.jpa.query.nativeMutate
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * A `@Serializable` Kotlin type in one column, through the mapper this module registers.
 *
 * Hibernate looks for Jackson, Jackson 3 and JSON-B and finds none of them here, so without that
 * mapper every scenario below fails at the first write with its "install Jackson or Yasson" message.
 * That makes the round trip a real assertion about the wiring and not only about the encoding.
 */
class JsonColumnTest :
    FeatureSpec({

        feature("a document column").config(enabled = JpaTestDatabase.available) {
            scenario("is jsonb, not bytes and not text") {
                JpaTestDatabase.withJpa(Customer::class) { jpa ->
                    // The only honest witness. A mapping that stored the whole object as `bytea`
                    // would round trip perfectly and agree with itself, which is why the converter
                    // specs ask the same question the same way.
                    JpaTestDatabase.columnType(jpa.config.schema!!, "customers", "address") shouldBe "jsonb"
                }
            }

            scenario("comes back as the value that went in") {
                JpaTestDatabase.withJpa(Customer::class) { jpa ->
                    val address = Address("Hauptstr 1", "Berlin", "DE", "second floor")
                    jpa.transaction { it.persist(Customer(1, address)) }

                    jpa.session { it.get<Customer>(1L) }.address shouldBe address
                }
            }

            scenario("holds a property that equalled its default, because SQL reads it") {
                JpaTestDatabase.withJpa(Customer::class) { jpa ->
                    val schema = jpa.config.schema!!
                    jpa.transaction { it.persist(Customer(1, Address("Hauptstr 1", "Berlin"))) }

                    // `country` was never set — it is the class's own default. kotlinx omits such a
                    // property unless told otherwise, and then this predicate matches nothing while
                    // the entity still reads back perfectly. That gap is what `jpaJson` closes.
                    jpa.session {
                        it
                            .nativeQuery<String>("""select address->>'country' from "$schema".customers where id = 1""")
                            .single()
                    } shouldBe "DE"
                }
            }

            scenario("writes a null property as a null key rather than leaving it out") {
                JpaTestDatabase.withJpa(Customer::class) { jpa ->
                    val schema = jpa.config.schema!!
                    jpa.transaction { it.persist(Customer(1, Address("Hauptstr 1", "Berlin"))) }

                    // `explicitNulls` is left at its default for the same reason `encodeDefaults` is
                    // not: a key that is absent and a key that is null are different questions in
                    // SQL, and only one of them can be asked of a document that omits it.
                    // `jsonb_exists` rather than the `?` operator: Hibernate parses a `?` in a
                    // native statement as an ordinal parameter and refuses the query for having no
                    // argument bound to it. The function is the same question without the clash.
                    jpa.session {
                        it
                            .nativeQuery<Boolean>(
                                """select jsonb_exists(address, 'note') from "$schema".customers where id = 1""",
                            ).single()
                    } shouldBe true
                }
            }

            scenario("still reads a document holding a key the class does not know") {
                JpaTestDatabase.withJpa(Customer::class) { jpa ->
                    val schema = jpa.config.schema!!
                    jpa.transaction { it.persist(Customer(1, Address("Hauptstr 1", "Berlin"))) }

                    // Written by a newer version of the class, or by another service, or by hand.
                    jpa.transaction {
                        it
                            .nativeMutate(
                                """
                                update "$schema".customers
                                set address = '{"street":"a","city":"b","country":"FR","note":null,"floor":3}'::jsonb
                                where id = 1
                                """.trimIndent(),
                            ).execute()
                    }

                    jpa.session { it.get<Customer>(1L) }.address.city shouldBe "b"
                }
            }
        }

        feature("changing a document").config(enabled = JpaTestDatabase.available) {
            scenario("is written when the value is replaced") {
                JpaTestDatabase.withJpa(Customer::class) { jpa ->
                    jpa.transaction { it.persist(Customer(1, label = Label("before"))) }
                    jpa.transaction { it.get<Customer>(1L).label = Label("after") }

                    jpa.session { it.get<Customer>(1L) }.label.text shouldBe "after"
                }
            }

            scenario("and is written when the value is mutated in place, which is not free") {
                JpaTestDatabase.withJpa(Customer::class) { jpa ->
                    jpa.transaction { it.persist(Customer(1, label = Label("before"))) }
                    jpa.transaction { it.get<Customer>(1L).label.text = "after" }

                    // Worth a scenario because the obvious guess is the other way: a type Hibernate
                    // does not map field by field has nothing to compare field by field. It compares
                    // the document instead — `FormatMapperBasedJavaType.deepCopy` is literally
                    // `fromString(toString(value))`, so the snapshot is a real copy and an edit is
                    // seen. The cost is that copy: every dirty check on a JSON attribute serializes
                    // and deserializes it, which is the argument for keeping such a column small.
                    jpa.session { it.get<Customer>(1L) }.label.text shouldBe "after"
                }
            }
        }

        feature("a null document").config(enabled = JpaTestDatabase.available) {
            scenario("is a null column, and never reaches the serializer") {
                JpaTestDatabase.withJpa(Customer::class) { jpa ->
                    val schema = jpa.config.schema!!
                    jpa.transaction { it.persist(Customer(1)) }
                    jpa.transaction {
                        it.nativeMutate("""update "$schema".customers set address = null where id = 1""").execute()
                    }

                    jpa
                        .session {
                            it
                                .nativeQuery<String>("""select address from "$schema".customers where id = 1""")
                                .singleOrNull()
                        }.shouldBeNull()
                }
            }
        }

        feature("a list").config(enabled = JpaTestDatabase.available) {
            scenario("is stored under the array code, which is a jsonb column like any other") {
                JpaTestDatabase.withJpa(Crate::class) { jpa ->
                    jpa.transaction { it.persist(Crate(1, listOf("x", "y"))) }

                    jpa.session { it.get<Crate>(1L) }.labels shouldBe listOf("x", "y")
                    JpaTestDatabase.columnType(jpa.config.schema!!, "crates", "labels") shouldBe "jsonb"
                }
            }

            scenario("under the object code is refused at startup, not at the first write") {
                val failure = shouldThrow<JpaMappingException> { JpaTestDatabase.withJpa(Basket::class) { } }

                // Without this check the schema exports happily and every write fails with Vert.x's
                // `DecodeException: Failed to decode` — the binder wrapping an array in a JsonObject
                // — which says nothing about the annotation that caused it.
                failure.message!! shouldContain "Basket.labels"
                failure.message!! shouldContain "SqlTypes.JSON_ARRAY"
            }

            scenario("and the mirror of that mistake is refused too") {
                val failure = shouldThrow<JpaMappingException> { JpaTestDatabase.withJpa(Bundle::class) { } }

                failure.message!! shouldContain "Bundle.address"
                failure.message!! shouldContain "SqlTypes.JSON"
            }
        }

        feature("a generic type").config(enabled = JpaTestDatabase.available) {
            scenario("keeps its type arguments, because the mapper resolves a reflective type") {
                JpaTestDatabase.withJpa(Customer::class) { jpa ->
                    // The one that would break if Hibernate handed the mapper an erased `Map`:
                    // kotlinx would answer with a polymorphic serializer and fail at the first write.
                    jpa.transaction { it.persist(Customer(1, tags = mapOf("tier" to "gold"))) }

                    jpa.session { it.get<Customer>(1L) }.tags shouldBe mapOf("tier" to "gold")
                    JpaTestDatabase.columnType(jpa.config.schema!!, "customers", "tags") shouldBe "jsonb"
                }
            }
        }

        feature("a type that is not @Serializable").config(enabled = JpaTestDatabase.available) {
            scenario("fails the write with this library's message, through all the reactive frames") {
                val failure =
                    shouldThrow<JpaSerializerException> {
                        JpaTestDatabase.withJpa(Box::class) { jpa ->
                            jpa.transaction { it.persist(Box(1, Plain("x"))) }
                        }
                    }

                // The reason `JpaException` exists: a failure thrown inside a binder, from a
                // CompletionStage, through a chain of Vert.x frames, still arrives saying what to fix.
                failure.message!! shouldContain "Plain"
                failure.message!! shouldContain "@Serializable"
            }
        }

        // The branch that fires on data an older version of the class wrote — the one member of
        // the sealed family a spec had never reached.
        feature("a stored document that does not decode") {
            scenario("is a JpaDocumentException naming the type, asked of the mapper directly") {
                val failure =
                    shouldThrow<JpaDocumentException> {
                        KotlinxJsonFormatMapper(jpaJson).decode("""{"street": 5}""", Address::class.java)
                    }

                failure.type shouldBe Address::class.java
                failure.message!! shouldContain "Address"
            }

            scenario("never puts the document in the message, because a stored value is somebody's") {
                val failure =
                    shouldThrow<JpaDocumentException> {
                        KotlinxJsonFormatMapper(jpaJson)
                            .decode("""{"street": "Geheimstr 1", "city": 7}""", Address::class.java)
                    }

                failure.message!! shouldNotContain "Geheimstr"
                // Not the cause's message either — kotlinx quotes the input in some of its own.
                failure.toString() shouldNotContain "Geheimstr"
            }
        }

        feature("a stored document that does not decode, out of the database")
            .config(enabled = JpaTestDatabase.available) {
                // It arrives wrapped: Hibernate catches what a FormatMapper throws and rethrows its
                // own. Pinned as it is rather than as it would be nicer — a caller that wants to
                // tell this apart from a real database failure walks the causes.
                scenario("reaches the caller with a JpaDocumentException in the cause chain") {
                    JpaTestDatabase.withJpa(Customer::class) { jpa ->
                        jpa.transaction { it.persist(Customer(1, Address("Hauptstr 1", "Berlin"))) }

                        // What a class that dropped or retyped a property leaves behind. Native SQL
                        // is not rewritten by `hibernate.default_schema`, so the table is qualified.
                        val table = "${jpa.config.schema}.customers"
                        jpa.transaction {
                            it
                                .nativeMutate("""update $table set address = '{"street": 5}'::jsonb where id = 1""")
                                .execute()
                        }

                        val failure = shouldThrowAny { jpa.session { it.get<Customer>(1L) } }

                        generateSequence(failure) { it.cause }
                            .filterIsInstance<JpaDocumentException>()
                            .first()
                            .type shouldBe Address::class.java
                    }
                }
            }

        feature("the message when a type is not @Serializable") {
            scenario("is asked of the mapper directly, since it needs no database to be wrong") {
                val failure = shouldThrow<JpaSerializerException> { KotlinxJsonFormatMapper(jpaJson).serializerFor(Plain::class.java) }

                failure.message!! shouldContain "Plain"
                failure.message!! shouldContain "@Serializable"
            }
        }
    })
