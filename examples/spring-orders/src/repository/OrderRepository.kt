package com.strange.example.orders.repository

import com.strange.common.page.Page
import com.strange.example.orders.model.Order
import com.strange.example.orders.model.OrderStatus
import com.strange.spring.data.mongo.criteria.all
import com.strange.spring.data.mongo.criteria.eq
import com.strange.spring.data.mongo.criteria.gte
import com.strange.spring.data.mongo.criteria.query
import com.strange.spring.data.mongo.template.MongoPage
import com.strange.spring.data.mongo.template.existsBy
import com.strange.spring.data.mongo.template.findAsFlow
import com.strange.spring.data.mongo.template.findPage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.stereotype.Repository

/**
 * Every Mongo call this application makes, and nothing else.
 *
 * The layer exists so that `template` appears in one file: a service that reaches for the template
 * directly ends up expressing a business rule and a query shape in the same expression, and the
 * rule becomes untestable without a database. Nothing here throws an `ApiException` or knows a
 * status code — a missing order is `null`, and what that means to a caller is the service's answer.
 *
 * It is a plain class over [ReactiveMongoTemplate] and not a `CrudRepository`: `stx-spring-boot`
 * offers extensions rather than a base class, because `findPage<Order>(window)` needs no `KClass`
 * and a class cannot have a `reified` type parameter.
 */
@Repository
class OrderRepository(
    private val template: ReactiveMongoTemplate,
) {
    /**
     * A page of orders, narrowed and ordered by [window].
     *
     * The window carries the filter as its `query` and the ordering as its `sort`, because the
     * cursor is derived from `sort` alone — an ordering that arrives baked into the query gives a
     * right-looking first page and an empty second one. `MongoPage` refuses that combination now.
     */
    suspend fun page(window: MongoPage): Page<Order> = template.findPage(window)

    /**
     * One order, or `null`.
     *
     * `findById` and not `("_id" eq id).query`: the id is a `String` here and an `ObjectId` in the
     * database, and only the first of those two goes through the converter that knows it.
     */
    suspend fun find(id: String): Order? = template.findById<Order>(id).awaitFirstOrNull()

    /** Whether [reference] is taken. `ref` is the stored name of `Order.reference`. */
    suspend fun existsByReference(reference: String): Boolean = template.existsBy<Order>(("ref" eq reference).query)

    /** Writes a new order. */
    suspend fun insert(order: Order): Order = template.insert(order).awaitSingle()

    /** Overwrites an existing one, so the audit trail records the transition and who made it. */
    suspend fun save(order: Order): Order = template.save(order).awaitSingle()

    /** Deletes one. The audit trail records a TERMINAL entry carrying its last known state. */
    suspend fun delete(order: Order) {
        template.remove(order).awaitSingle()
    }

    /** Every paid order at or above [floor] — a predicate built in Kotlin rather than parsed. */
    suspend fun paidAtLeast(floor: Long): List<Order> =
        template
            .findAsFlow<Order>(all(Order::status eq OrderStatus.PAID.name, Order::total gte floor).query)
            .toList()
}
