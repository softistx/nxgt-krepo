package com.softistx.example.orders.service

import com.softistx.example.orders.api.apis.IOrdersService
import com.softistx.example.orders.api.models.ChangeStatusRequest
import com.softistx.example.orders.api.models.OrderList
import com.softistx.example.orders.api.models.OrderPage
import com.softistx.example.orders.api.models.OrderResponse
import com.softistx.example.orders.api.models.PlaceOrderRequest
import com.softistx.example.orders.mapper.list
import com.softistx.example.orders.mapper.page
import com.softistx.example.orders.mapper.response
import com.softistx.example.orders.mapper.toDomain
import com.softistx.example.orders.mapper.toEntity
import com.softistx.example.orders.model.Order
import com.softistx.example.orders.repository.OrderRepository
import com.softistx.spring.data.mongo.filter.parseFilter
import com.softistx.spring.data.mongo.filter.toSort
import com.softistx.spring.data.mongo.template.MongoPage
import com.softistx.spring.error.ApiException
import com.softistx.spring.web.DEFAULT_PAGE_SIZE
import com.softistx.spring.web.parseSort
import com.softistx.telemetry.logger
import com.softistx.telemetry.span
import org.springframework.stereotype.Service

/**
 * Where this file's logs say they came from.
 *
 * A top-level `val` and not an injected bean, because there is nothing to inject: `stx.telemetry.enabled`
 * *installs* the root, so `logger<T>()` finds it from anywhere — an `init` block, a `catch`, a class
 * Spring never built. With no telemetry installed at all every call here is a silent no-op, which is
 * what lets a library log without insisting the application configure one.
 */
private val log = logger<OrderService>()

/** What a caller is told when an order is not there. Also a key in `resources/locales/`. */
const val KEY_ORDER_NOT_FOUND = "orders.not-found"

/** What a caller is told when a reference is already taken. */
const val KEY_REFERENCE_TAKEN = "orders.reference-taken"

/**
 * What a caller is told when it asks for a status this service has never heard of.
 *
 * The text names no value, and that is not an oversight: the generated enum decodes any unlisted
 * string to `UNKNOWN`, whose `wireValue` is a sentinel, so by the time this is raised the string the
 * client actually sent is gone. Echoing `wireValue` would answer `__unknown__ is not a status`,
 * which is true of nothing the client wrote.
 */
const val KEY_UNKNOWN_STATUS = "orders.unknown-status"

/**
 * The rules, and the only place that decides what a failure is.
 *
 * **It implements [IOrdersService], the same generated interface the controller implements.** That
 * is what makes the contract hold one layer down: a document change that adds a parameter breaks
 * this file too, rather than being absorbed by a controller that quietly drops it. The
 * `@HttpExchange` annotations it inherits are inert here — only `@Controller`-annotated beans are
 * scanned for request mappings.
 *
 * **No `try`/`catch` anywhere.** A rule throws `ApiException` and `ApiExceptionHandler` answers it,
 * translated for whoever asked. That is `stx.errors.enabled` in `application.yaml` and nothing else
 * — there is no advice class in this application.
 */
@Service
class OrderService(
    private val orders: OrderRepository,
) : IOrdersService {
    /**
     * `GET /orders?filter=status:eq:PAID&sort=placedAt:DESC&size=20&cursor=…`
     *
     * The four parameters are parsed here and turned into one [MongoPage], because the ordering has
     * to reach `MongoPage.sort` and not the `Query` — spelling that out at each call site is how the
     * cursor and the rows come to disagree. A functional route gets the same window from one
     * `request.mongoPage()`; a controller is handed the values already bound, so it builds it here.
     */
    override suspend fun findOrders(
        filter: String?,
        sort: String?,
        size: Int?,
        cursor: String?,
    ): OrderPage =
        orders
            .page(
                MongoPage.first(
                    size = size?.takeIf { it > 0 } ?: DEFAULT_PAGE_SIZE,
                    cursor = cursor,
                    query = filter.parseFilter(),
                    sort = sort.parseSort().toSort(),
                ),
            ).page()

    /** `GET /orders/valuable?floor=10000` — the same store, queried from Kotlin instead of a query string. */
    override suspend fun valuableOrders(floor: Long): OrderList = orders.paidAtLeast(floor).list()

    /** `GET /orders/{id}` — or a translated 404. */
    override suspend fun findOrder(id: String): OrderResponse = get(id).response()

    /**
     * `POST /orders` — 201, or a translated 409 when the reference is taken.
     *
     * The duplicate check is a query rather than a caught `DuplicateKeyException`, which is worth
     * being honest about: two requests racing can both pass it, and the unique index on `ref` is
     * what actually decides. The check is here so the ordinary case gets a translated 409 instead of
     * a driver exception, not because it is a lock.
     *
     * **The only [span] in this application, and that is the lesson.** Every request already has a
     * server span — `stx.telemetry.web-filter` opens one in a `CoWebFilter`, and this one nests
     * inside it automatically, because the span context is a `CoroutineContext.Element` and a
     * suspending handler is inside the filter's coroutine. So a span per method would time the same
     * work twice under a second name. One is here because *placing* an order is two round trips to
     * Mongo behind one route, and the split between them is a thing somebody will want to see.
     *
     * The rest of the file logs and does not span, which is the ordinary case.
     */
    override suspend fun placeOrder(body: PlaceOrderRequest): OrderResponse =
        span("place order", "reference" to body.reference) {
            if (orders.existsByReference(body.reference)) {
                log.warn(ReferenceTaken(body.reference))
                throw ApiException.conflict(KEY_REFERENCE_TAKEN, mapOf("reference" to body.reference))
            }
            val order = orders.insert(body.toEntity())
            // On this span alone. The `reference` passed above is inherited by the log written below
            // and by anything nested; an attribute set on the receiver is not.
            attribute("orderId", order.id)
            log.info(OrderPlaced(order.reference, order.total))
            order.response()
        }

    /**
     * `PATCH /orders/{id}/status` — a save, so the audit trail records the transition.
     *
     * The order is read into a local rather than saved in one expression, because the log wants the
     * status it *had*. That is the shape a typed event tends to force, and it is the right one: a
     * record of `PAID` with nothing to compare it to answers half the question.
     */
    override suspend fun changeStatus(
        id: String,
        body: ChangeStatusRequest,
    ): OrderResponse {
        val status =
            body.status.toDomain()
                ?: throw ApiException.badRequest(KEY_UNKNOWN_STATUS)
        val order = get(id)
        val changed = orders.save(order.copy(status = status))
        log.info(OrderStatusChanged(changed.reference, order.status, status))
        return changed.response()
    }

    /** `DELETE /orders/{id}` — 204, and a `TERMINAL` entry in the trail. */
    override suspend fun cancelOrder(id: String) {
        orders.delete(get(id))
    }

    /**
     * One order or a translated 404 — the read every write does first.
     *
     * Not on [IOrdersService]: the document describes an HTTP surface, and this is not part of one.
     * A hand-written port would have room for it with a `TODO()` default body; a generated one does
     * not, which is the better outcome — it belongs here.
     */
    private suspend fun get(id: String): Order = orders.find(id) ?: throw ApiException.notFound(KEY_ORDER_NOT_FOUND, mapOf("id" to id))
}
