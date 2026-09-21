package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.spring.SpringExtension

/**
 * What lets a Kotest spec here be a Spring test.
 *
 * Kotest looks for this exact class in this exact package. `stx-spring-boot` ships a
 * `SpringProjectConfig` that does exactly this, and `spring-orders` uses it — but this module
 * deliberately does not depend on `stx-spring-boot`. Pulling in a fourth library to prove three
 * unrelated ones would put its POM between the check and what it checks, which is the opposite of
 * the point. So the one line it would have inherited is written out.
 */
object ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(SpringExtension())
}
