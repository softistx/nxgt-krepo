package com.strange.openapi.models

import com.strange.openapi.ApiModel
import com.strange.openapi.Field
import com.strange.openapi.ObjectType
import com.strange.openapi.TypeRef
import com.strange.openapi.UnionDiscriminator
import com.strange.openapi.UnionSubtype
import com.strange.openapi.UnionType
import com.strange.openapi.render
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private val PET =
    UnionType(
        name = "Pet",
        subtypes = listOf(UnionSubtype("Dog", "dog"), UnionSubtype("Cat", "cat")),
        discriminator = UnionDiscriminator(name = "petType", wireName = "petType"),
        fallback = "UnknownPet",
    )

private val DOG =
    ObjectType(
        name = "Dog",
        fields =
            listOf(
                Field("petType", "petType", TypeRef.StringRef, required = true, overrides = true, constant = "dog"),
                Field("bark", "bark", TypeRef.StringRef, required = true),
            ),
        implements = listOf("Pet"),
    )

private val PAYMENT =
    UnionType(
        name = "Payment",
        subtypes =
            listOf(
                UnionSubtype("CardPayment", null, listOf("cardLast4")),
                UnionSubtype("BankPayment", null, listOf("iban")),
            ),
        discriminator = null,
        fallback = null,
    )

private fun render(
    style: ModelStyle,
    name: String,
) = ModelsOnlyEmitter(style)
    .render(ApiModel(groups = emptyList(), models = listOf(PET, DOG, PAYMENT)))
    .getValue("com.example.api.models.$name")

/**
 * The two libraries reach the same wire format by opposite routes, and the interesting failures are
 * all about the discriminator being a property the document already declares.
 */
class UnionEmitterTest :
    FeatureSpec({

        feature("the shape both styles share") {
            scenario("the union is a sealed interface its members implement") {
                render(ModelStyle.Kotlinx, "Pet") shouldContain "public sealed interface Pet"
                render(ModelStyle.Kotlinx, "Dog") shouldContain ") : Pet"
            }

            scenario("a discriminated union carries a tolerant fallback subtype") {
                render(ModelStyle.Kotlinx, "Pet") shouldContain "public data class UnknownPet"
                // Nothing to put in one when members are told apart by shape
                render(ModelStyle.Kotlinx, "Payment") shouldNotContain "UnknownPayment"
            }

            scenario("the subtype declares the discriminator as a constant it overrides") {
                render(ModelStyle.Jackson, "Dog") shouldContain """override val petType: String = "dog""""
            }
        }

        feature("kotlinx binding") {
            scenario("a content-polymorphic serializer selects, so the property writes itself once") {
                val source = render(ModelStyle.Kotlinx, "Pet")

                // kotlinx's own polymorphism would write a second discriminator and then refuse
                source shouldContain "@Serializable(with = PetSerializer::class)"
                source shouldContain "JsonContentPolymorphicSerializer<Pet>(Pet::class)"
                source shouldContain """"dog" -> Dog.serializer()"""
                source shouldContain "else -> UnknownPet.serializer()"
            }

            scenario("the constant is always encoded, not treated as a skippable default") {
                render(ModelStyle.Kotlinx, "Dog") shouldContain "@EncodeDefault(EncodeDefault.Mode.ALWAYS)"
            }

            scenario("a union told apart by shape tries the most specific variant first") {
                val source = render(ModelStyle.Kotlinx, "Payment")

                source shouldContain """"cardLast4" in keys -> CardPayment.serializer()"""
                source shouldContain "throw SerializationException"
            }
        }

        feature("jackson binding") {
            scenario("the existing property supplies the tag, and stays visible to the constructor") {
                val source = render(ModelStyle.Jackson, "Pet")

                // As.PROPERTY would write petType a second time; without visible the Kotlin
                // property never gets bound.
                source shouldContain "include = JsonTypeInfo.As.EXISTING_PROPERTY"
                source shouldContain "visible = true"
                source shouldContain "defaultImpl = UnknownPet::class"
                source shouldContain """JsonSubTypes.Type(value = Dog::class, name = "dog")"""
            }

            scenario("a union with no discriminator is deduced from its properties") {
                val source = render(ModelStyle.Jackson, "Payment")

                source shouldContain "use = JsonTypeInfo.Id.DEDUCTION"
                source shouldContain "JsonSubTypes.Type(value = CardPayment::class)"
            }
        }
    })
