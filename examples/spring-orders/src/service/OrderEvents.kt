package com.strange.example.orders.service

import com.strange.example.orders.model.OrderStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What this service logs, declared.
 *
 * `log.info(OrderPlaced(order.reference, order.total))` rather than
 * `log.info("placed order " + order.reference)`, and the difference is not formatting. The type's serial
 * name becomes the event's name and its fields become the attributes, so **choosing what is logged
 * is the same act as writing the type** — which is the answer to the problem every logging library
 * has with sensitive data. An `Order` handed to a `toString()` logs whatever fields it happens to
 * have, including the one added next quarter, and nobody finds out.
 *
 * Which is why nothing here carries [com.strange.example.orders.model.Order.customer]. The customer
 * is in `orders` where it belongs; a telemetry collection with a seven-day TTL is not the place for
 * a second copy of it, and the way that decision is enforced is that this file does not mention it.
 *
 * `@SerialName` is what makes the name something to query rather than a package path:
 * `{ "name": "orders.placed" }` as a Mongo filter, `select(.name == "orders.placed")` as a jq one.
 */
@Serializable
@SerialName("orders.placed")
data class OrderPlaced(
    val reference: String,
    /** Minor units, as `Order.total` is. An order total is not a `Double`. */
    val total: Long,
)

/** A transition, with both ends — a record of `PAID` alone does not say what it came from. */
@Serializable
@SerialName("orders.status-changed")
data class OrderStatusChanged(
    val reference: String,
    val from: OrderStatus,
    val to: OrderStatus,
)

/**
 * A reference that was already taken.
 *
 * Logged at `warn` and not `error`: a caller being told no is this service working. The 409 it
 * becomes is `ApiException.conflict`, raised on the next line — the log is here because the
 * *rejection rate* is a thing worth watching, and a translated message on its way to one client is
 * not where anybody will see it.
 */
@Serializable
@SerialName("orders.reference-taken")
data class ReferenceTaken(
    val reference: String,
)
