package com.softistx.spring.web

import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

// The value first, the status second: `product.created()` rather than
// `ServerResponse.status(CREATED).bodyValueAndAwait(product)`. A handler's last line should read as
// what it returns. These are the four statuses a resource route actually answers with; anything
// else is `ServerResponse` directly, which is still right there.

/** 200 with this as the body. */
suspend fun Any.ok(): ServerResponse = ServerResponse.ok().bodyValueAndAwait(this)

/** 201 with this as the body — the created resource, which is what a client wants back. */
suspend fun Any.created(): ServerResponse = ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(this)

/** 202 with this as the body, for work that has been accepted and not yet done. */
suspend fun Any.accepted(): ServerResponse = ServerResponse.accepted().bodyValueAndAwait(this)

/** 204, no body. An extension on nothing, because there is nothing to send. */
suspend fun noContent(): ServerResponse = ServerResponse.noContent().buildAndAwait()
