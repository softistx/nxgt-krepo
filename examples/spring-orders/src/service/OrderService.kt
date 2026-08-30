package com.strange.example.orders.service

import com.strange.example.orders.api.apis.IOrdersService
import com.strange.example.orders.api.models.ChangeStatusRequest
import com.strange.example.orders.api.models.OrderList
import com.strange.example.orders.api.models.OrderPage
import com.strange.example.orders.api.models.OrderResponse
import com.strange.example.orders.api.models.PlaceOrderRequest
import com.strange.example.orders.mapper.list
import com.strange.example.orders.mapper.page
import com.strange.example.orders.mapper.response
import com.strange.example.orders.mapper.toDomain
import com.strange.example.orders.mapper.toEntity
import com.strange.example.orders.model.Order
import com.strange.example.orders.repository.OrderRepository
import com.strange.spring.data.mongo.filter.parseFilter
import com.strange.spring.data.mongo.filter.toSort
import com.strange.spring.data.mongo.template.MongoPage
import com.strange.spring.error.ApiException
import com.strange.spring.web.DEFAULT_PAGE_SIZE
import com.strange.spring.web.parseSort
import org.springframework.stereotype.Service

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
     */
    override suspend fun placeOrder(body: PlaceOrderRequest): OrderResponse {
        if (orders.existsByReference(body.reference)) {
            throw ApiException.conflict(KEY_REFERENCE_TAKEN, mapOf("reference" to body.reference))
        }
        return orders.insert(body.toEntity()).response()
    }

    /** `PATCH /orders/{id}/status` — a save, so the audit trail records the transition. */
    override suspend fun changeStatus(
        id: String,
        body: ChangeStatusRequest,
    ): OrderResponse {
        val status =
            body.status.toDomain()
                ?: throw ApiException.badRequest(KEY_UNKNOWN_STATUS)
        return orders.save(get(id).copy(status = status)).response()
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
