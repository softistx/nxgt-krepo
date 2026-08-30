package com.strange.spring.testing

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * The smallest application `src/testing/` can be specced against.
 *
 * It declares nothing. That is the claim: an application that adds [MongoSpec] to a bare
 * `@SpringBootApplication` gets a MongoDB, a port and a `WebTestClient` with no bootstrap of its own,
 * and every stx integration auto-configuration stays dark because each is
 * `@ConditionalOnProperty(enabled = true)`.
 *
 * `@TestConfiguration` classes are excluded from component scanning, so the two in this package
 * reach the context through `@Import` on the base spec and only there — which is what makes them
 * opt-in rather than something every consumer of this library gets.
 */
@SpringBootApplication
class TestingApplication
