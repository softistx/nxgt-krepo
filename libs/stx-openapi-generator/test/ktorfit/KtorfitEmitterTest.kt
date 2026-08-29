package com.strange.openapi.ktorfit

import com.strange.openapi.render
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class KtorfitEmitterTest :
    FeatureSpec({

        val files = KtorfitEmitter().render()
        val api = files.getValue("com.example.api.apis.CategoriesApi")
        val category = files.getValue("com.example.api.models.Category")

        feature("interfaces") {
            scenario("emits one suspend function per operation with its HTTP annotation") {
                api shouldContain "public interface CategoriesApi"
                api shouldContain """@GET("categories/{id}")"""
                api shouldContain """@POST("categories")"""
                api shouldContain "public suspend fun findCategory("
            }

            scenario("return types resolve to model classes in the model package") {
                api shouldContain "import com.example.api.models.Category"
                api shouldContain "): Category"
                api shouldContain "): List<Category>"
                // KotlinPoet omits an explicit Unit return type
                api shouldContain "public suspend fun changePhoto("
            }
        }

        feature("parameters") {
            scenario("annotates parameters by kind and makes optional ones nullable with a default") {
                api shouldContain """@Path("id") id: String"""
                api shouldContain """@Query("cursor") cursor: String? = null"""
                api shouldContain "@Body body: CategoryRequest"
            }

            scenario("operations with a body declare the JSON Content-Type") {
                // without it Ktor fails at runtime with "Content-Type: null"
                api shouldContain """@Headers("Content-Type: application/json")"""
            }
        }

        feature("multipart") {
            scenario("multipart operations get @Multipart and @Part") {
                api shouldContain "@Multipart"
                // KotlinPoet backticks `file`, which is a soft keyword; still valid Kotlin
                api shouldContain """@Part("file")"""
                api shouldContain "ByteArray"
            }

            scenario("@Part stays non-nullable even when the spec marks the part optional") {
                // ktorfit-ksp fails the build with "Part parameter type may not be nullable"
                api shouldNotContain "ByteArray?"
            }
        }

        feature("models") {
            scenario("models are serializable data classes with SerialName only where names differ") {
                category shouldContain "@Serializable"
                category shouldContain "public data class Category"
                category shouldContain """@SerialName("created_at")"""
                category shouldContain "createdAt: Instant? = null"
                // the stdlib type, not the deprecated kotlinx.datetime typealias
                category shouldContain "import kotlin.time.Instant"
                category shouldContain "meta: JsonObject? = null"
                // `id` matches the wire name, so it needs no @SerialName
                category.substringAfter("public val id").substringBefore("\n") shouldNotContain "SerialName"
            }
        }
    })
