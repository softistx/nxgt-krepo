package com.strange.spring.web

import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * Several routers joined at once, instead of `.and(...).and(...).and(...)`.
 *
 * ```kotlin
 * @Bean
 * fun routes() = productRoutes.and(orderRoutes, cartRoutes, healthRoutes)
 * ```
 *
 * Folded left, so the order a caller writes is the order Spring matches in — which matters the
 * moment two routers claim overlapping paths.
 */
fun RouterFunction<ServerResponse>.and(vararg routes: RouterFunction<ServerResponse>): RouterFunction<ServerResponse> =
    routes.fold(this) { combined, route -> combined.and(route) }
