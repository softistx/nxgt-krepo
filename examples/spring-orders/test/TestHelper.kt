package com.strange.example.orders

import com.strange.example.orders.api.utils.apiErrorFilter
import com.strange.example.orders.api.utils.apiOperationProcessor
import com.strange.example.orders.api.utils.registerApiEnumConverters
import com.strange.spring.client.generatedApiFactory
import com.strange.spring.testing.TestServer
import kotlinx.serialization.json.Json
import org.springframework.http.HttpHeaders
import org.springframework.web.service.invoker.HttpServiceProxyFactory

/**
 * The generated clients' factory — this module's four generated symbols, and nothing else.
 *
 * **The assembly is the library's; only the arguments are this module's.**
 * `com.strange.spring.client.generatedApiFactory` knows which four settings a generated interface
 * needs and what each one silently costs when it is left out; the generator emits the symbols
 * themselves into `<packageName>.utils`, so they are passed rather than named. What is left here is
 * a list of four, which is the point — a spec that spelled the assembly again would be a second copy
 * of a decision, and the copy is the one that goes stale.
 *
 * [json] is the application's own `stxWebJson` bean, injected into the spec and handed down, so the
 * client reads exactly what the server wrote. [headers] runs per request, which is where an API with
 * authentication puts its token — `apiFactory(json) { it.setBearerAuth(token) }`, the seam
 * `nxgt-rest`'s specs use. This one authenticates nobody, so it is empty here and present anyway,
 * because it is the parameter a real API's spec reaches for first.
 */
fun apiFactory(
    json: Json,
    baseUrl: String = TestServer.baseUrl,
    headers: (HttpHeaders) -> Unit = {},
): HttpServiceProxyFactory =
    generatedApiFactory(
        baseUrl,
        json = json,
        enums = ::registerApiEnumConverters,
        operations = apiOperationProcessor(),
        filters = listOf(apiErrorFilter()),
        headers = headers,
    )
