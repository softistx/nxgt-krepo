package com.strange.spring.json

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.http.codec.CodecCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@Serializable
private data class Product(
    val name: String,
    val nickname: String? = null,
    val stock: Int = 0,
)

class JsonAutoConfigurationTest :
    StringSpec({
        val runner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JsonAutoConfiguration::class.java))

        "Jackson stays the codec until an application says otherwise" {
            runner.run { context -> context.getBeanNamesForType(CodecCustomizer::class.java).size shouldBe 0 }
        }

        "stx.json.enabled contributes a codec customizer" {
            // A customizer rather than a WebFluxConfigurer: an application that has its own
            // configurer would find two of them competing, while customizers compose.
            runner.withPropertyValues("stx.json.enabled=true").run { context ->
                context.getBeanNamesForType(CodecCustomizer::class.java).size shouldBe 1
            }
        }

        "a null field is left out, and a defaulted one is written" {
            runner.withPropertyValues("stx.json.enabled=true").run { context ->
                val json = context.getBean(Json::class.java).encodeToString(Product(name = "Hammer"))

                json shouldNotContain "nickname"
                // The opposite of the kotlinx default, and deliberately: a client that has never
                // seen `stock` cannot know what the server would have used for it.
                json shouldContain "\"stock\":0"
            }
        }

        "explicit nulls can be turned back on" {
            runner
                .withPropertyValues("stx.json.enabled=true", "stx.json.explicit-nulls=true")
                .run { context ->
                    context.getBean(Json::class.java).encodeToString(Product(name = "Hammer")) shouldContain
                        "\"nickname\":null"
                }
        }

        "and defaults can be turned back off" {
            runner
                .withPropertyValues("stx.json.enabled=true", "stx.json.encode-defaults=false")
                .run { context ->
                    context.getBean(Json::class.java).encodeToString(Product(name = "Hammer")) shouldNotContain "stock"
                }
        }

        "encode-defaults wins over explicit-nulls for a property whose default is null" {
            // The two settings are not independent, which is worth knowing before someone turns on
            // explicit-nulls and finds their nulls still missing: a property equal to its default is
            // dropped first, and for `nickname` the default *is* null.
            runner
                .withPropertyValues(
                    "stx.json.enabled=true",
                    "stx.json.explicit-nulls=true",
                    "stx.json.encode-defaults=false",
                ).run { context ->
                    context.getBean(Json::class.java).encodeToString(Product(name = "Hammer")) shouldNotContain "nickname"
                }
        }

        "unknown keys are still ignored, because that came from stx-common" {
            // A reader that throws on a field a newer writer added stops during every rolling
            // deploy. `lenientJson` is the base this is built from, not a starting point it drifts
            // away from.
            runner.withPropertyValues("stx.json.enabled=true").run { context ->
                context
                    .getBean(Json::class.java)
                    .decodeFromString<Product>("""{"name":"Hammer","colour":"red"}""")
                    .name shouldBe "Hammer"
            }
        }
    })
