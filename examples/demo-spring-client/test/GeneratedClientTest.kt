package com.strange.demo.spring

import com.strange.demo.spring.api.apis.CategoriesApi
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

/**
 * Reads the generated interfaces back through reflection.
 *
 * That the module compiles already proves the emitted source is valid Kotlin against spring-web;
 * this goes one step further and checks the annotations Spring actually reads at proxy-creation
 * time are present with the values the spec asked for.
 */
class GeneratedClientTest :
    FeatureSpec({

        feature("the generated interface") {
            scenario("the generated interface is annotated for Spring") {
                CategoriesApi::class.java.getAnnotation(HttpExchange::class.java).shouldNotBeNull()
            }
        }

        feature("operation bindings") {
            scenario("an operation carries its verb, url and path bindings") {
                val method = CategoriesApi::class.java.methods.single { it.name == "findCategory" }

                method.getAnnotation(GetExchange::class.java).url shouldBe "categories/{id}"
                method.parameterAnnotations
                    .flatMap { it.toList() }
                    .filterIsInstance<PathVariable>()
                    .single()
                    .name shouldBe "id"
            }

            scenario("an operation with a body declares its content type") {
                val method = CategoriesApi::class.java.methods.single { it.name == "findCategories" }

                val exchange = method.getAnnotation(PostExchange::class.java)
                exchange.url shouldBe "categories/search"
                exchange.contentType shouldBe "application/json"
                method.parameterAnnotations
                    .flatMap { it.toList() }
                    .filterIsInstance<RequestBody>()
                    .shouldNotBeEmpty()
            }

            scenario("optional query parameters opt out of Spring's required-value check") {
                val method = CategoriesApi::class.java.methods.single { it.name == "findCategories" }

                val cursor =
                    method.parameterAnnotations
                        .flatMap { it.toList() }
                        .filterIsInstance<RequestParam>()
                        .single { it.name == "cursor" }
                // Spring's argument resolver throws on a null value for a required named parameter
                cursor.required shouldBe false
            }
        }
    })
