package com.softistx.graphix.spring

import kotlinx.coroutines.reactor.mono
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import reactor.core.publisher.Mono

/**
 * `GET [path]` serving one HTML page. Its own file rather than a branch in [GraphixHandler], which
 * is about the JSON envelope — this shares none of it.
 */
internal fun sandboxRouter(
    path: String,
    html: String,
): RouterFunction<ServerResponse> =
    RouterFunctions
        .route()
        .GET(path) { _: ServerRequest -> sandboxResponse(html) }
        .build()

private fun sandboxResponse(html: String): Mono<ServerResponse> =
    mono {
        ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .bodyValueAndAwait(html)
    }
