package com.strange.example.orders.migration

import com.strange.example.orders.model.Order
import com.strange.example.orders.model.OrderStatus
import com.strange.spring.data.mongo.migration.Migration
import com.strange.spring.data.mongo.migration.MigrationUnit
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * Puts three orders in an empty database, so a fresh checkout has something to page through.
 *
 * **The class name is the version.** `V1Seed` is order 1 — `stx.data.mongo.migration.prefix` is
 * `V`, the digits are the order, and the rest is a name for a human. Rename it and the runner will
 * not recognise it; give another unit order 1 and the run refuses to start rather than picking one.
 *
 * It runs once. `MigrationRunner` records it in `migrations` on success, so the second start finds
 * it already applied and does nothing — which is why this is a migration and not an
 * `ApplicationReadyEvent` listener that checks whether the collection is empty.
 */
@MigrationUnit("seeds three demo orders")
class V1Seed(
    private val template: ReactiveMongoTemplate,
) : Migration {
    override suspend fun migrate() {
        listOf(
            Order(reference = "A-1001", customer = "ada", status = OrderStatus.PAID, total = 24_990),
            Order(reference = "A-1002", customer = "grace", status = OrderStatus.PENDING, total = 4_500),
            Order(reference = "A-1003", customer = "ada", status = OrderStatus.PAID, total = 132_000),
        ).forEach { template.insert(it).awaitFirstOrNull() }
    }
}
