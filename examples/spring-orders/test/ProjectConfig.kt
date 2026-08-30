package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension

/**
 * What lets a Kotest spec be a Spring test.
 *
 * Kotest looks for this exact class in this exact package, and the extension is what makes
 * `@SpringBootTest` mean anything: it prepares the test context around each spec and autowires the
 * beans a spec declares in its constructor. Without it the annotations on `OrdersSpec` are inert and
 * every spec fails on a missing bean, which is a confusing way to learn this file was deleted.
 */
object ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(SpringExtension())
}
