package com.strange.demo.spring

import com.strange.demo.spring.api.apis.CategoriesApi
import com.strange.demo.spring.api.apis.FailuresApi
import com.strange.demo.spring.api.apis.NotificationsApi
import com.strange.demo.spring.api.apis.SessionApi
import com.strange.demo.spring.api.apis.TagsApi
import com.strange.demo.spring.api.utils.ApiAuthConfig
import com.strange.demo.spring.api.utils.apiAuthFilter
import com.strange.demo.spring.api.utils.apiErrorFilter
import com.strange.demo.spring.api.utils.apiOperationProcessor
import com.strange.demo.spring.api.utils.registerApiEnumConverters
import kotlinx.coroutines.runBlocking
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

/**
 * Everything under `com.strange.demo.spring.api` is generated from `../demo-api/openapi.yaml` with
 * `client: Spring`. Unlike the Ktorfit client there is no annotation processing step: the
 * interfaces are handed to [HttpServiceProxyFactory], which builds the implementation at runtime.
 *
 * The proxy needs a *reactive* adapter because the generated functions are `suspend`;
 * `RestClientAdapter` would not do.
 */
public class SpringDemoClient(
    baseUrl: String,
    /**
     * The bearer token to attach to the operations the document says need one — and to nothing
     * else. Called per request, so a token that expires can be replaced behind it.
     */
    token: (suspend () -> String?)? = null,
) {
    // Generated: one credential slot per scheme the document declares. The same class the Ktorfit
    // client configures, because a credential is a fact about the document, not about the client.
    private val credentials = ApiAuthConfig().apply { bearer = token }

    private val webClient =
        WebClient
            .builder()
            // The JDK connector keeps this to spring-webflux plus the JDK — no Reactor Netty.
            .clientConnector(JdkClientHttpConnector())
            .baseUrl(baseUrl)
            // Generated: turns a documented failure into the exception the document describes,
            // reading the operation out of the attribute apiOperationProcessor() put there.
            .filter(apiAuthFilter(credentials))
            .filter(apiErrorFilter())
            .build()

    // Generated: without it Spring writes an enum argument as its Kotlin name rather than the
    // value the document lists, and a query filter silently matches nothing.
    private val conversions = DefaultFormattingConversionService().also(::registerApiEnumConverters)

    private val factory =
        HttpServiceProxyFactory
            .builderFor(WebClientAdapter.create(webClient))
            .conversionService(conversions)
            // Generated: the proxy knows the method, the filter sees the response, and this is
            // what carries the operation from one to the other.
            .httpRequestValuesProcessor(apiOperationProcessor())
            .build()

    public val categories: CategoriesApi = factory.createClient(CategoriesApi::class.java)
    public val tags: TagsApi = factory.createClient(TagsApi::class.java)
    public val notifications: NotificationsApi = factory.createClient(NotificationsApi::class.java)
    public val failures: FailuresApi = factory.createClient(FailuresApi::class.java)
    public val session: SessionApi = factory.createClient(SessionApi::class.java)
}

public fun main(): Unit =
    runBlocking {
        val client = SpringDemoClient(System.getenv("DEMO_API_URL") ?: "http://127.0.0.1:8080/")
        println("categories: " + client.categories.findCategory("1"))
    }
