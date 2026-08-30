package com.strange.spring.testing

import io.kotest.core.spec.style.FeatureSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * The application, started the way Spring starts it.
 *
 * **A spec declares what it needs and Spring provides it.** `@SpringBootTest` builds the context,
 * binds the port the `test` profile names, and hands the beans a spec asks for to its constructor.
 * There is no `SpringApplicationBuilder` to write, no context to close and no port to discover, and
 * Spring's test context cache keys on the merged configuration — so every spec extending this gets
 * one application rather than one each. That is also why such specs *clean* rather than isolate, and
 * why none of them may depend on another having run.
 *
 * ```kotlin
 * class HealthTest(json: Json) : SpringSpec({
 *     feature("GET /health") { … }
 * })
 * ```
 *
 * A subclass that re-annotates wins: `@SpringBootTest(webEnvironment = RANDOM_PORT)` or a different
 * `@ActiveProfiles` is one annotation, not a bootstrap of its own. [MongoSpec] is this with a MongoDB
 * behind it; `io.kotest.provided.ProjectConfig` must extend [SpringProjectConfig], or none of these
 * annotations mean anything.
 */
@ActiveProfiles(TEST_PROFILE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Import(TestServerConfiguration::class)
abstract class SpringSpec(
    body: FeatureSpec.() -> Unit,
) : FeatureSpec(body)

/** The profile `testResources/application-test.yaml` is written against. */
const val TEST_PROFILE = "test"
