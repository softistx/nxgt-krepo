package com.strange.example.orders

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * An order book over MongoDB — the smallest thing that shows `stx-spring-boot` end to end.
 *
 * ```
 * MONGO_URI=mongodb://localhost:27017 ./kotlin run -m spring-orders
 * ```
 *
 * There is no `@Configuration` class here and no wiring beyond this file, which is the point: the
 * error handler, the locale resolver, the CORS filter, the Mongo converters, the audit trail and the
 * migration runner are all auto-configurations that `resources/application.yaml` switches on. What
 * an application writes is its own routes and its own documents.
 *
 * `@SpringBootApplication` and not `@ComponentScan` + `@ConfigurationPropertiesScan` + the rest:
 * `stx-spring-boot` registers itself through `AutoConfiguration.imports`, so nothing here has to
 * name a package of somebody else's.
 */
@SpringBootApplication
class OrdersApplication

fun main(args: Array<String>) {
    runApplication<OrdersApplication>(*args)
}
