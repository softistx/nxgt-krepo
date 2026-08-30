package com.strange.spring.testing

import io.kotest.core.spec.style.FeatureSpec
import org.springframework.context.annotation.Import

/**
 * [SpringSpec] with a MongoDB behind it.
 *
 * The only thing it adds is [MongoTestConfiguration], which answers where that MongoDB is — a
 * container, or the server `MONGO_TEST_URI` names — and which database inside it this module gets.
 * An application declares that database in `testResources/application-test.yaml` and writes no Kotlin:
 *
 * ```yaml
 * spring:
 *   mongodb:                            # `spring.mongodb`, not `spring.data.mongodb` — Boot 4 split them
 *     database: spring_orders_test      # a prefix: `testDatabase` appends this run's suffix
 * ```
 *
 * ```kotlin
 * class OrderControllerTest(template: ReactiveMongoTemplate, json: Json) : MongoSpec({
 *     beforeEach { template.clear("orders", "audits") }
 *
 *     feature("POST /orders").config(enabled = mongoAvailable) { … }
 * })
 * ```
 *
 * Gate every feature that touches the server on [mongoAvailable]: a machine with neither Docker nor
 * `MONGO_TEST_URI` then reports skipped tests instead of failing a build over something that is not
 * the code.
 *
 * **[TestServerConfiguration] is named again rather than inherited.** `@Import` on a test class is
 * collected by Boot's `ImportsContextCustomizerFactory`, and whether a subclass's list adds to its
 * superclass's or replaces it is not a thing to leave to inference. Naming both costs a word, and
 * Spring de-duplicates a configuration class imported twice.
 */
@Import(TestServerConfiguration::class, MongoTestConfiguration::class)
abstract class MongoSpec(
    body: FeatureSpec.() -> Unit,
) : SpringSpec(body)
