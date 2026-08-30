package com.strange.example.orders.rest

import com.strange.example.orders.api.apis.IOrdersService
import com.strange.example.orders.api.models.ChangeStatusRequest
import com.strange.example.orders.api.models.PlaceOrderRequest
import com.strange.example.orders.service.OrderService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The whole HTTP surface, and none of it written here.
 *
 * There is no `@RequestMapping` and no `@GetMapping` in this file: the paths, the verbs and the
 * parameter bindings are on [IOrdersService], which the openapi plugin generates from
 * `openapi/api-docs.yaml`. Spring reads `@HttpExchange` off an implemented interface — the mapping
 * searches the whole type hierarchy — so an endpoint cannot disagree with the document, because
 * nobody typed it twice.
 *
 * What is left is the two things the interface cannot carry:
 *
 * - **A status other than 200**, which is `@ResponseStatus` on the override. The document says 201
 *   for a placed order and 204 for a cancelled one; the interface has nowhere to put that.
 * - **The parameter bindings, repeated.** Spring does not reliably inherit parameter annotations,
 *   and the failure is a 400 at run time rather than anything at build time.
 *
 * It takes [OrderService] and not `IOrdersService`: the service implements the same interface, so
 * two beans satisfy it and by-type injection would be ambiguous.
 */
@RestController
class OrderController(
    private val service: OrderService,
) : IOrdersService {
    override suspend fun findOrders(
        @RequestParam filter: String?,
        @RequestParam sort: String?,
        @RequestParam size: Int?,
        @RequestParam cursor: String?,
    ) = service.findOrders(filter, sort, size, cursor)

    override suspend fun valuableOrders(
        @RequestParam floor: Long,
    ) = service.valuableOrders(floor)

    override suspend fun findOrder(
        @PathVariable id: String,
    ) = service.findOrder(id)

    @ResponseStatus(HttpStatus.CREATED)
    override suspend fun placeOrder(
        @RequestBody body: PlaceOrderRequest,
    ) = service.placeOrder(body)

    override suspend fun changeStatus(
        @PathVariable id: String,
        @RequestBody body: ChangeStatusRequest,
    ) = service.changeStatus(id, body)

    @ResponseStatus(HttpStatus.NO_CONTENT)
    override suspend fun cancelOrder(
        @PathVariable id: String,
    ) = service.cancelOrder(id)
}
