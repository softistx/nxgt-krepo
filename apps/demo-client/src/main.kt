package com.strange.demo.client

import de.jensklingenberg.ktorfit.Ktorfit
import com.strange.demo.client.api.CategoriesApi
import com.strange.demo.client.api.TagsApi
import com.strange.demo.client.api.createCategoriesApi
import com.strange.demo.client.api.createTagsApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import com.strange.demo.client.api.model.CategoryRequest
import com.strange.demo.client.api.model.SearchRequest

/**
 * Everything under `com.strange.demo.client.api` is generated: the `openapi-client` plugin turns
 * `../demo-api/openapi.yaml` into the annotated interfaces, and ktorfit-ksp then generates the
 * `createXxxApi()` builders below from those interfaces.
 */
public class DemoClient(baseUrl: String) : AutoCloseable {
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    private val ktorfit = Ktorfit.Builder().baseUrl(baseUrl).httpClient(http).build()

    // createCategoriesApi(), never create<CategoriesApi>(): without Ktorfit's compiler plugin
    // the generic form does not resolve to the generated implementation.
    public val categories: CategoriesApi = ktorfit.createCategoriesApi()
    public val tags: TagsApi = ktorfit.createTagsApi()

    override fun close(): Unit = http.close()
}

public fun main(): Unit = runBlocking {
    val baseUrl = System.getenv("DEMO_API_URL") ?: "http://127.0.0.1:8080/"
    DemoClient(baseUrl).use { client ->
        val created = client.categories.createCategory(CategoryRequest(name = "books", family = "media"))
        println("created ${created.id} -> ${created.name}")
        val page = client.categories.findCategories(SearchRequest(), first = 10)
        println("categories: ${page.data?.map { it.name }}")
    }
}
