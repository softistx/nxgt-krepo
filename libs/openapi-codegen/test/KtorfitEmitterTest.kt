package dev.nxgt.openapi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private fun emit(model: ClientModel, options: EmitOptions = EmitOptions("com.example.api")): Map<String, String> =
    KtorfitEmitter().emit(model, options).associate { "${it.packageName}.${it.name}" to it.toString() }

class KtorfitEmitterTest : FunSpec({

    val model = ClientModel(
        groups = listOf(
            ApiGroup(
                name = "CategoriesApi",
                operations = listOf(
                    Operation(
                        name = "findCategory",
                        httpMethod = "GET",
                        path = "categories/{id}",
                        parameters = listOf(
                            Param("id", "id", ParamKind.Path, TypeRef.StringRef, required = true),
                            Param("cursor", "cursor", ParamKind.Query, TypeRef.StringRef, required = false),
                        ),
                        returnType = TypeRef.ModelRef("Category"),
                        summary = "Get category by ID",
                    ),
                    Operation(
                        name = "createCategory",
                        httpMethod = "POST",
                        path = "categories",
                        parameters = listOf(
                            Param("body", "body", ParamKind.Body, TypeRef.ModelRef("CategoryRequest"), required = true),
                        ),
                        returnType = TypeRef.ListRef(TypeRef.ModelRef("Category")),
                    ),
                    Operation(
                        name = "changePhoto",
                        httpMethod = "PUT",
                        path = "categories/{id}/photo",
                        parameters = listOf(
                            Param("file", "file", ParamKind.Part, TypeRef.BinaryRef, required = true),
                        ),
                        returnType = TypeRef.UnitRef,
                    ),
                ),
            ),
        ),
        models = listOf(
            ModelType(
                name = "Category",
                fields = listOf(
                    Field("id", "id", TypeRef.StringRef, required = true),
                    Field("createdAt", "created_at", TypeRef.InstantRef, required = false),
                    Field("meta", "meta", TypeRef.JsonObjectRef, required = false),
                ),
            ),
        ),
    )

    val files = emit(model)
    val api = files.getValue("com.example.api.CategoriesApi")
    val category = files.getValue("com.example.api.model.Category")

    test("emits one suspend function per operation with its HTTP annotation") {
        api shouldContain "public interface CategoriesApi"
        api shouldContain """@GET("categories/{id}")"""
        api shouldContain """@POST("categories")"""
        api shouldContain "public suspend fun findCategory("
    }

    test("annotates parameters by kind and makes optional ones nullable with a default") {
        api shouldContain """@Path("id") id: String"""
        api shouldContain """@Query("cursor") cursor: String? = null"""
        api shouldContain "@Body body: CategoryRequest"
    }

    test("operations with a body declare the JSON Content-Type") {
        // without it Ktor fails at runtime with "Content-Type: null"
        api shouldContain """@Headers("Content-Type: application/json")"""
    }

    test("multipart operations get @Multipart and @Part") {
        api shouldContain "@Multipart"
        // KotlinPoet backticks `file`, which is a soft keyword; still valid Kotlin
        api shouldContain """@Part("file")"""
        api shouldContain "ByteArray"
    }

    test("@Part stays non-nullable even when the spec marks the part optional") {
        // ktorfit-ksp fails the build with "Part parameter type may not be nullable"
        api shouldNotContain "ByteArray?"
    }

    test("return types resolve to model classes in the model package") {
        api shouldContain "import com.example.api.model.Category"
        api shouldContain "): Category"
        api shouldContain "): List<Category>"
        // KotlinPoet omits an explicit Unit return type
        api shouldContain "public suspend fun changePhoto("
    }

    test("models are serializable data classes with SerialName only where names differ") {
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

    test("model generation can be switched off") {
        val without = emit(model, EmitOptions("com.example.api", generateModels = false))
        without.keys.none { it.startsWith("com.example.api.model") } shouldBe true
    }

    test("summaries carry through as KDoc") {
        api shouldContain "Get category by ID"
    }
})
