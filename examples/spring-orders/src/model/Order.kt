package com.strange.example.orders.model

import com.strange.spring.data.mongo.audit.Auditable
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import kotlin.time.Clock
import kotlin.time.Instant

/** Where an order has got to. */
enum class OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    CANCELLED,
}

/**
 * One order.
 *
 * **`@Auditable` is the whole opt-in for the audit trail.** Every save and delete of this document
 * appends a version carrying its whole state, what changed, and who changed it — and no other
 * document in an application gets one unless it says so too.
 *
 * [placedAt] is a `kotlin.time.Instant`, which is exactly the field type that fails at *query* time
 * with `Can't find a codec` unless something registered the converters. `stx.data.mongo.enabled`
 * is that something; `docs/spring-mongo-queries.md` has the reasoning.
 *
 * [reference] is stored as `ref` to make a point the paging rules depend on: a cursor carries the
 * *stored* field name, so sorting by `reference` and paging through it only works because the
 * cursor is built from the mapping rather than from the property name.
 */
@Auditable
@Document("orders")
data class Order(
    @Id val id: String = ObjectId().toHexString(),
    @Indexed(unique = true) @Field("ref") val reference: String,
    val customer: String,
    val status: OrderStatus = OrderStatus.PENDING,
    /** Minor units — an order total is not a `Double`, and rounding a price is not a display choice. */
    val total: Long,
    val tags: List<String> = emptyList(),
    @Indexed val placedAt: Instant = Clock.System.now(),
)
