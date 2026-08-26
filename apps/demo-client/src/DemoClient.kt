package com.strange.demo.client

import com.strange.demo.client.api.apis.CategoriesApi
import com.strange.demo.client.api.apis.FailuresApi
import com.strange.demo.client.api.apis.NotificationsApi
import com.strange.demo.client.api.apis.SessionApi
import com.strange.demo.client.api.apis.TagsApi
import com.strange.demo.client.api.apis.createCategoriesApi
import com.strange.demo.client.api.apis.createFailuresApi
import com.strange.demo.client.api.apis.createNotificationsApi
import com.strange.demo.client.api.apis.createSessionApi
import com.strange.demo.client.api.apis.createTagsApi
import com.strange.demo.client.api.models.CategoryRequest
import com.strange.demo.client.api.models.SearchRequest
import com.strange.demo.client.api.utils.ApiAuth
import com.strange.demo.client.api.utils.ApiErrors
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking

/**
 * Everything under `com.strange.demo.client.api` is generated: the `openapi-client` plugin turns
 * `../demo-api/openapi.yaml` into the annotated interfaces, and ktorfit-ksp then generates the
 * `createXxxApi()` builders below from those interfaces.
 */
public class DemoClient(
    baseUrl: String,
    /**
     * The bearer token to attach to the operations the document says need one — and to nothing
     * else. Called per request, so a token that expires can be replaced behind it.
     */
    token: (suspend () -> String?)? = null,
) : AutoCloseable {
    /** The URL this client was built against, so a test can build a second one beside it. */
    public val baseUrl: String = baseUrl

    private val http =
        HttpClient(CIO) {
            install(ContentNegotiation) { json() }
            // Generated: without it a documented failure reaches the caller as a deserialization
            // error about the success type, and the status and body the document describes are lost.
            install(ApiErrors)
            // Generated: attaches the credential each operation's `security` asks for. The
            // document's sign-in operations override the root with `security: []`, so this is
            // also what keeps the token off the endpoints that hand it out.
            install(ApiAuth) { bearer = token }
        }

    private val ktorfit =
        Ktorfit
            .Builder()
            .baseUrl(baseUrl)
            .httpClient(http)
            .build()

    // createCategoriesApi(), never create<CategoriesApi>(): without Ktorfit's compiler plugin
    // the generic form does not resolve to the generated implementation.
    public val categories: CategoriesApi = ktorfit.createCategoriesApi()
    public val tags: TagsApi = ktorfit.createTagsApi()
    public val notifications: NotificationsApi = ktorfit.createNotificationsApi()
    public val failures: FailuresApi = ktorfit.createFailuresApi()
    public val session: SessionApi = ktorfit.createSessionApi()

    override fun close(): Unit = http.close()
}

public fun main(): Unit =
    runBlocking {
        val baseUrl = System.getenv("DEMO_API_URL") ?: "http://127.0.0.1:8080/"
        DemoClient(baseUrl).use { client ->
            val created = client.categories.createCategory(CategoryRequest(name = "books", family = "media"))
            println("created ${created.id} -> ${created.name}")
            val page = client.categories.findCategories(SearchRequest(), first = 10)
            println("categories: ${page.data?.map { it.name }}")
        }
    }
