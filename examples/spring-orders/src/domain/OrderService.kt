package com.strange.example.orders.domain

import com.strange.common.page.Page
import com.strange.example.orders.model.PlaceOrder
import com.strange.spring.data.mongo.criteria.all
import com.strange.spring.data.mongo.criteria.eq
import com.strange.spring.data.mongo.criteria.gte
import com.strange.spring.data.mongo.criteria.query
import com.strange.spring.data.mongo.template.MongoPage
import com.strange.spring.data.mongo.template.existsBy
import com.strange.spring.data.mongo.template.findAsFlow
import com.strange.spring.data.mongo.template.findPage
import com.strange.spring.error.ApiException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.stereotype.Service

/** What a caller is told when an order is not there. Also a key in `resources/locales/`. */
const val KEY_ORDER_NOT_FOUND = "orders.not-found"

/** What a caller is told when a reference is already taken. */
const val KEY_REFERENCE_TAKEN = "orders.reference-taken"

/**
 * Reading and writing orders.
 *
 * A plain `@Service` over `ReactiveMongoTemplate` — there is no repository interface, and nothing in
 * `stx-spring-boot` provides a base class to extend. What a base class would have earned is here as
 * extensions instead: `findPage<Order>(window)` needs no `KClass` because it is `reified`, and a
 * class cannot be.
 */
@Service
class OrderService(
    private val template: ReactiveMongoTemplate,
) {
    /**
     * A page of orders, narrowed and ordered by whatever the request asked for.
     *
     * [window] arrives already built, by `request.mongoPage()` in `OrderRoutes`. The service does
     * not parse a query string, and that separation is deliberate: a filter grammar that reached
     * into a service would put URL parsing behind the business logic.
     *
     * It is also the only spelling that pages correctly. The window carries the filter as its
     * `query` and the ordering as its `sort`, because the cursor is derived from `sort` alone — an
     * ordering that arrives baked into the query gives a right-looking first page and an empty
     * second one. `MongoPage` refuses that combination outright now, which is how it stays fixed.
     */
    suspend fun page(window: MongoPage): Page<Order> = template.findPage(window)

    /**
     * One order, or a translated 404.
     *
     * `findById` and not `("_id" eq id).query`: the id is a `String` here and an `ObjectId` in the
     * database, and only the first of those two goes through the converter that knows it.
     */
    suspend fun get(id: String): Order =
        template.findById<Order>(id).awaitFirstOrNull()
            ?: throw ApiException.notFound(KEY_ORDER_NOT_FOUND, mapOf("id" to id))

    /**
     * Places an order.
     *
     * The duplicate check is a query rather than a caught `DuplicateKeyException`, which is worth
     * being honest about: two requests racing can both pass it, and the unique index on `ref` is
     * what actually decides. The check is here so the ordinary case gets a translated 409 instead of
     * a driver exception, not because it is a lock.
     */
    suspend fun place(request: PlaceOrder): Order {
        if (template.existsBy<Order>(("ref" eq request.reference).query)) {
            throw ApiException.conflict(KEY_REFERENCE_TAKEN, mapOf("reference" to request.reference))
        }
        return template
            .insert(
                Order(
                    reference = request.reference,
                    customer = request.customer,
                    total = request.total,
                    tags = request.tags,
                ),
            ).awaitSingle()
    }

    /** Moves an order along. A save, so the audit trail records the transition and who made it. */
    suspend fun changeStatus(
        id: String,
        status: OrderStatus,
    ): Order = template.save(get(id).copy(status = status)).awaitSingle()

    /** Deletes one. The audit trail records a TERMINAL entry carrying its last known state. */
    suspend fun cancel(id: String) {
        template.remove(get(id)).awaitSingle()
    }

    /** What the catalogue is worth, for orders at or above [floor] — a predicate built in Kotlin. */
    suspend fun valuable(floor: Long): List<Order> =
        template
            .findAsFlow<Order>(all(Order::status eq OrderStatus.PAID.name, Order::total gte floor).query)
            .toList()
}
