package com.softistx.openapi.parser

import com.softistx.openapi.ObjectType
import com.softistx.openapi.TypeRef
import com.softistx.openapi.UnionType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun spec(schemas: String) =
    """
    |openapi: 3.0.1
    |info: {title: t, version: "1"}
    |paths:
    |  /a: {get: {tags: [t], operationId: a, responses: {'200': {description: ok}}}}
    |components:
    |  schemas:
    |$schemas
    """.trimMargin()

private val SPEC =
    spec(
        """
        |    Pet:
        |      oneOf:
        |        - {${'$'}ref: '#/components/schemas/Dog'}
        |        - {${'$'}ref: '#/components/schemas/Cat'}
        |      discriminator:
        |        propertyName: petType
        |        mapping: {dog: '#/components/schemas/Dog'}
        |    PetBase:
        |      type: object
        |      required: [petType, name]
        |      properties: {petType: {type: string}, name: {type: string}}
        |    Dog:
        |      allOf:
        |        - {${'$'}ref: '#/components/schemas/PetBase'}
        |        - {type: object, required: [bark], properties: {bark: {type: string}}}
        |    Cat:
        |      allOf:
        |        - {${'$'}ref: '#/components/schemas/PetBase'}
        |        - {type: object, properties: {livesLeft: {type: integer}}}
        |    Payment:
        |      oneOf:
        |        - {${'$'}ref: '#/components/schemas/CardPayment'}
        |        - {${'$'}ref: '#/components/schemas/BankPayment'}
        |    CardPayment: {type: object, required: [cardLast4], properties: {cardLast4: {type: string}}}
        |    BankPayment: {type: object, required: [iban], properties: {iban: {type: string}}}
        """.trimMargin(),
    )

private val MODELS = OpenApiParser().parse(SPEC).models

private fun union(name: String) = MODELS.filterIsInstance<UnionType>().first { it.name == name }

private fun obj(name: String) = MODELS.filterIsInstance<ObjectType>().first { it.name == name }

/**
 * Composition is where a document stops being a list of shapes and starts describing a hierarchy.
 * Both halves used to be dropped: a `oneOf` schema has no `properties` so nothing was emitted, and
 * an `allOf` schema's inherited fields were read by nobody.
 */
class CompositionTest :
    FeatureSpec({

        feature("allOf") {
            scenario("branches and local properties merge into one class") {
                obj("Dog").fields.map { it.wireName } shouldContainExactly listOf("petType", "name", "bark")
            }

            scenario("required is the union across branches") {
                obj("Cat").let { cat ->
                    cat.fields.first { it.wireName == "name" }.required shouldBe true
                    cat.fields.first { it.wireName == "livesLeft" }.required shouldBe false
                }
            }

            scenario("branches that disagree about a property fail rather than letting one win") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
                                |    A: {type: object, properties: {id: {type: string}}}
                                |    B: {type: object, properties: {id: {type: integer}}}
                                |    C:
                                |      allOf:
                                |        - {${'$'}ref: '#/components/schemas/A'}
                                |        - {${'$'}ref: '#/components/schemas/B'}
                                """.trimMargin(),
                            ),
                        )
                    }

                error.message shouldContain "'id'"
                error.message shouldContain "StringRef"
                error.message shouldContain "IntRef"
            }
        }

        feature("discriminated unions") {
            scenario("the union is a type, and its members implement it") {
                union("Pet").subtypes.map { it.name } shouldContainExactly listOf("Dog", "Cat")
                obj("Dog").implements shouldContainExactly listOf("Pet")
                obj("Cat").implements shouldContainExactly listOf("Pet")
            }

            scenario("an explicit mapping wins, and an unmapped member uses its schema name") {
                union("Pet").subtypes.associate { it.name to it.wireValue } shouldBe
                    mapOf("Dog" to "dog", "Cat" to "Cat")
            }

            scenario("the discriminator becomes a constant the subtype always writes") {
                obj("Dog").fields.first { it.wireName == "petType" }.let {
                    it.overrides shouldBe true
                    it.constant shouldBe "dog"
                    it.required shouldBe true
                }
            }

            scenario("a tolerant fallback subtype is named for tags the document does not list") {
                union("Pet").fallback shouldBe "UnknownPet"
            }

            scenario("a mapping naming a schema outside the union fails") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
                                |    X: {type: object, properties: {a: {type: string}}}
                                |    Y: {type: object, properties: {b: {type: string}}}
                                |    Z: {type: object, properties: {c: {type: string}}}
                                |    U:
                                |      oneOf: [{${'$'}ref: '#/components/schemas/X'}, {${'$'}ref: '#/components/schemas/Y'}]
                                |      discriminator: {propertyName: kind, mapping: {zed: '#/components/schemas/Z'}}
                                """.trimMargin(),
                            ),
                        )
                    }

                error.message shouldContain "Z"
            }
        }

        feature("unions told apart by shape") {
            scenario("a union with no discriminator records what distinguishes each member") {
                union("Payment").let {
                    it.discriminator.shouldBeNull()
                    it.subtypes.associate { subtype -> subtype.name to subtype.distinguishingKeys } shouldBe
                        mapOf("CardPayment" to listOf("cardLast4"), "BankPayment" to listOf("iban"))
                }
            }

            scenario("it gets no fallback, because there is no tag to put in one") {
                union("Payment").fallback.shouldBeNull()
            }

            scenario("members nothing can tell apart fail at parse rather than at the first request") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
                                |    L: {type: object, properties: {id: {type: string}}}
                                |    R: {type: object, properties: {id: {type: string}}}
                                |    U: {oneOf: [{${'$'}ref: '#/components/schemas/L'}, {${'$'}ref: '#/components/schemas/R'}]}
                                """.trimMargin(),
                            ),
                        )
                    }

                error.message shouldContain "no property that tells them apart"
            }
        }

        feature("what is not a union") {
            scenario("a oneOf with a null branch is a nullable type, not a hierarchy") {
                val parsed =
                    OpenApiParser().parse(
                        spec(
                            """
                            |    Cat2: {type: object, properties: {name: {type: string}}}
                            |    Holder:
                            |      type: object
                            |      properties:
                            |        pet: {oneOf: [{${'$'}ref: '#/components/schemas/Cat2'}, {type: 'null'}]}
                            """.trimMargin(),
                        ),
                    )
                val pet =
                    parsed.models
                        .filterIsInstance<ObjectType>()
                        .first { it.name == "Holder" }
                        .fields
                        .single()

                pet.type shouldBe TypeRef.ModelRef("Cat2")
                pet.nullable shouldBe true
                parsed.models.filterIsInstance<UnionType>() shouldContainExactly emptyList()
            }

            scenario("a union over scalars stays raw JSON, since a scalar cannot implement an interface") {
                val parsed =
                    OpenApiParser().parse(
                        spec(
                            """
                            |    Mixed: {oneOf: [{type: string}, {type: integer}]}
                            |    Holder: {type: object, properties: {v: {${'$'}ref: '#/components/schemas/Mixed'}}}
                            """.trimMargin(),
                        ),
                    )
                parsed.models.filterIsInstance<UnionType>() shouldContainExactly emptyList()
                parsed.models
                    .filterIsInstance<ObjectType>()
                    .first { it.name == "Holder" }
                    .fields
                    .single()
                    .type shouldBe TypeRef.JsonObjectRef
            }
        }
    })
