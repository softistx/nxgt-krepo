package com.strange.openapi.spring

import com.strange.openapi.render
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SpringEmitterTest : FunSpec({

    val files = SpringEmitter().render()
    val api = files.getValue("com.example.api.CategoriesApi")
    val category = files.getValue("com.example.api.model.Category")

    test("emits an @HttpExchange interface of suspend functions") {
        api shouldContain "@HttpExchange"
        api shouldContain "public interface CategoriesApi"
        api shouldContain "public suspend fun findCategory("
        // KotlinPoet backticks `annotation`, a Kotlin soft keyword; the import is still valid
        api shouldContain "import org.springframework.web.service.`annotation`.HttpExchange"
    }

    test("maps each HTTP method to its exchange annotation") {
        api shouldContain """@GetExchange(url = "categories/{id}")"""
        api shouldContain """url = "categories""""
        api shouldContain "@PostExchange"
        api shouldContain "@PutExchange"
    }

    test("annotates parameters with the spring-web binding annotations") {
        api shouldContain """@PathVariable(name = "id") id: String"""
        api shouldContain "@RequestBody body: CategoryRequest"
        api shouldContain """@RequestPart(name = "file""""
    }

    test("optional named parameters say required = false") {
        // Spring's argument resolver throws on a null value for a required named parameter
        api shouldContain """@RequestParam(name = "cursor", required = false) cursor: String? = null"""
    }

    test("a nullable @RequestPart is allowed, unlike Ktorfit's @Part") {
        api shouldContain "ByteArray? = null"
    }

    test("declares the content type of requests that carry a body") {
        api shouldContain """contentType = "application/json""""
        api shouldContain """contentType = "multipart/form-data""""
        // a plain GET carries no body, so it gets no content type
        api.substringAfter("@GetExchange").substringBefore(")") shouldNotContain "contentType"
    }

    test("models are plain data classes bound by Jackson") {
        category shouldContain "public data class Category"
        category shouldNotContain "@Serializable"
        // only a wire name that differs from the Kotlin name pulls in a Jackson annotation
        category shouldContain """@JsonProperty("created_at")"""
        category.substringAfter("public val id").substringBefore("\n") shouldNotContain "JsonProperty"
    }

    test("uses the types Jackson knows how to bind") {
        category shouldContain "import java.time.Instant"
        category shouldContain "createdAt: Instant? = null"
        // a free-form object binds to a Map, so no Jackson type leaks into the model
        category shouldContain "meta: Map<String, Any?>? = null"
    }

})
