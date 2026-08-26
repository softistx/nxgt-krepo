package com.strange.demo.spring

import com.strange.demo.spring.api.CategoriesApi
import com.strange.demo.spring.api.TagsApi

/**
 * Compiles the Spring output of the `openapi-client` plugin.
 *
 * Everything under `com.strange.demo.spring.api` is generated from `../demo-api/openapi.yaml`
 * with `client: Spring`. Unlike the Ktorfit client there is no annotation processing step: the
 * interfaces are handed to `HttpServiceProxyFactory` at runtime, so what has to be verified
 * here is that they compile against spring-web and carry the annotations Spring will read.
 *
 * ```kotlin
 * val factory = HttpServiceProxyFactory
 *     .builderFor(WebClientAdapter.create(WebClient.create(baseUrl)))  // needs spring-webflux
 *     .build()
 * val categories = factory.createClient(CategoriesApi::class.java)
 * ```
 */
public fun main() {
    listOf(CategoriesApi::class.java, TagsApi::class.java).forEach { api ->
        println("${api.simpleName}: ${api.methods.size} operation(s)")
    }
}
