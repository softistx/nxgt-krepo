package com.strange.example.orders.mapper

import com.strange.common.page.Page
import com.strange.example.orders.api.models.OrderList
import com.strange.example.orders.api.models.OrderPage
import com.strange.example.orders.api.models.OrderResponse
import com.strange.example.orders.api.models.PlaceOrderRequest
import com.strange.example.orders.model.Order
import com.strange.example.orders.model.OrderStatus
import com.strange.example.orders.api.models.Order as ApiOrder
import com.strange.example.orders.api.models.OrderStatus as ApiOrderStatus
import com.strange.example.orders.api.models.PageInfo as ApiPageInfo

/**
 * The document's types on one side, the database's on the other.
 *
 * Pure functions, no Spring, and the only place the two vocabularies meet. Everything here is
 * mechanical on purpose: a mapper that starts making decisions is a service that has been misfiled.
 *
 * **The stored [OrderStatus] and the generated [ApiOrderStatus] stay separate types.** They spell
 * the same four values today, but the generated one carries an `UNKNOWN` sentinel so a client can
 * read a status a newer server invented — and a sentinel is exactly what must never reach the
 * database. Mapping by name costs one function and keeps the collection out of the contract's reach.
 */
fun Order.view(): ApiOrder =
    ApiOrder(
        id = id,
        reference = reference,
        customer = customer,
        status = ApiOrderStatus.valueOf(status.name),
        total = total,
        tags = tags,
        placedAt = placedAt,
    )

/** One order, in the envelope every response here uses. */
fun Order.response(): OrderResponse = OrderResponse(data = view())

/** Several, with no page around them. */
fun List<Order>.list(): OrderList = OrderList(data = map { it.view() })

/**
 * A page: the rows in `data`, the cursors in `metadata`.
 *
 * The two halves separate here rather than at the client, which is the whole reason
 * `com.strange.common.page.Page` exists — a list that has lost its cursors cannot ask for the
 * next page.
 */
fun Page<Order>.page(): OrderPage =
    OrderPage(
        data = data.map { it.view() },
        metadata =
            ApiPageInfo(
                startCursor = info.startCursor,
                endCursor = info.endCursor,
                hasPreviousPage = info.hasPreviousPage,
                hasNextPage = info.hasNextPage,
            ),
    )

/** What a placed order starts as. The id, the status and the timestamp are the entity's defaults. */
fun PlaceOrderRequest.toEntity(): Order =
    Order(
        reference = reference,
        customer = customer,
        total = total,
        tags = tags ?: emptyList(),
    )

/**
 * The stored status a client asked for, or `null` when it asked for one this service cannot name.
 *
 * The generated enum decodes any unlisted string to `UNKNOWN` rather than throwing, which is right
 * for a *client* reading a newer server. Here it arrives on the way in, so it is a bad request —
 * and saying so is the service's decision, not this file's, which is why the answer is `null`.
 */
fun ApiOrderStatus.toDomain(): OrderStatus? = if (this == ApiOrderStatus.UNKNOWN) null else OrderStatus.valueOf(name)
