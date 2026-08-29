package com.strange.example.orders.model

import com.strange.example.orders.domain.Order
import com.strange.example.orders.domain.OrderStatus
import kotlinx.serialization.Serializable

/** What a client sends to place an order. */
@Serializable
data class PlaceOrder(
    val reference: String,
    val customer: String,
    val total: Long,
    val tags: List<String> = emptyList(),
)

/** What a client sends to move one along. */
@Serializable
data class ChangeStatus(
    val status: OrderStatus,
)

/**
 * What an order looks like on the wire.
 *
 * A view rather than the document itself, and the reason is `placedAt`: the entity holds a
 * `kotlin.time.Instant`, and which JSON that becomes depends on whether the application turned
 * `stx.json` on — Jackson writes `{"epochSeconds":…}` and kotlinx writes ISO-8601. A response shape
 * that changes with a codec setting is not a contract, so the view spells the timestamp as the text
 * it should be. `ErrorResponse.timestamp` is a `String` for exactly this reason.
 */
@Serializable
data class OrderView(
    val id: String,
    val reference: String,
    val customer: String,
    val status: OrderStatus,
    val total: Long,
    val tags: List<String>,
    val placedAt: String,
)

fun Order.view(): OrderView =
    OrderView(
        id = id,
        reference = reference,
        customer = customer,
        status = status,
        total = total,
        tags = tags,
        placedAt = placedAt.toString(),
    )

/**
 * What `/health` answers with.
 *
 * A type and not a `Map<String, String>`, because `stx.json` is on: kotlinx resolves a serializer
 * from the declared type, and a map handed to `bodyValue` arrives having lost its type arguments.
 */
@Serializable
data class Health(
    val status: String,
)
