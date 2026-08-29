package com.strange.example.orders.routes

import com.strange.example.orders.domain.OrderService
import com.strange.example.orders.model.ChangeStatus
import com.strange.example.orders.model.Health
import com.strange.example.orders.model.PlaceOrder
import com.strange.example.orders.model.view
import com.strange.spring.data.mongo.filter.mongoPage
import com.strange.spring.web.and
import com.strange.spring.web.body
import com.strange.spring.web.created
import com.strange.spring.web.id
import com.strange.spring.web.noContent
import com.strange.spring.web.ok
import com.strange.spring.web.requiredParam
import com.strange.spring.web.response
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

/**
 * The whole HTTP surface, as functions.
 *
 * Functional routing rather than `@RestController`, because that is what `stx-spring`'s `web/`
 * package is built for: `request.body<PlaceOrder>()`, `request.id`, `request.mongoPage()` and
 * `something.ok()` are all extensions on `ServerRequest`, and a handler that uses them has nothing
 * in it but what the route actually does.
 *
 * **No `try`/`catch` anywhere.** A handler throws `ApiException` and `ApiExceptionHandler` answers
 * it, translated for whoever asked. That is `stx.errors.enabled` in `application.yaml` and nothing
 * else — there is no advice class in this application.
 */
@Configuration(proxyBeanMethods = false)
class OrderRoutes(
    private val orders: OrderService,
) {
    /**
     * Two routers joined with `stx-spring`'s `and`, which folds left so the written order is the
     * matched order.
     *
     * Named `orderRouter` and not `orderRoutes`: a `@Bean` method may not share a name with the
     * `@Configuration` class that declares it, since component scanning already registered that
     * class under the decapitalized class name. Spring refuses the context outright, which is the
     * good outcome — it is the kind of collision that would otherwise depend on registration order.
     */
    @Bean
    fun orderRouter(): RouterFunction<ServerResponse> =
        coRouter {
            "/orders".nest {
                GET("", ::list)
                GET("/valuable", ::valuable)
                POST("", ::place)
                GET("/{id}", ::one)
                PATCH("/{id}/status", ::changeStatus)
                DELETE("/{id}", ::cancel)
            }
        }.and(coRouter { GET("/health") { Health("UP").response().ok() } })

    /**
     * `GET /orders?filter=status:eq:PAID&sort=placedAt:DESC&size=20&cursor=…`
     *
     * `request.mongoPage()` reads all four and puts each where it belongs. It is one call rather
     * than four on purpose: the ordering has to reach `MongoPage.sort` and not the `Query`, and
     * spelling that out by hand is how the cursor and the rows come to disagree.
     */
    private suspend fun list(request: ServerRequest) =
        orders
            .page(request.mongoPage())
            .map { it.view() }
            .response()
            .ok()

    /** `GET /orders/valuable?floor=10000` — the same store, queried from Kotlin instead of a query string. */
    private suspend fun valuable(request: ServerRequest) =
        orders
            .valuable(request.requiredParam("floor").toLong())
            .map { it.view() }
            .response()
            .ok()

    /** `GET /orders/{id}` — or a translated 404 this handler never sees. */
    private suspend fun one(request: ServerRequest) =
        orders
            .get(request.id)
            .view()
            .response()
            .ok()

    /** `POST /orders` — 201, or a translated 409 when the reference is taken. */
    private suspend fun place(request: ServerRequest) =
        orders
            .place(request.body<PlaceOrder>())
            .view()
            .response()
            .created()

    /** `PATCH /orders/{id}/status` — a save, so the audit trail records the transition. */
    private suspend fun changeStatus(request: ServerRequest) =
        orders
            .changeStatus(request.id, request.body<ChangeStatus>().status)
            .view()
            .response()
            .ok()

    /** `DELETE /orders/{id}` — 204, and a `TERMINAL` entry in the trail. */
    private suspend fun cancel(request: ServerRequest): ServerResponse {
        orders.cancel(request.id)
        return noContent()
    }
}
