package com.softistx.spring.testing

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.spring.SpringExtension

/**
 * What lets a Kotest spec be a Spring test.
 *
 * Kotest looks for `io.kotest.provided.ProjectConfig` by that exact name in that exact package, so
 * this library cannot own the file — but it can own what goes in it. An application writes:
 *
 * ```kotlin
 * package io.kotest.provided
 *
 * object ProjectConfig : SpringProjectConfig()
 * ```
 *
 * [SpringExtension] is what makes the annotations on [SpringSpec] mean anything: it prepares the test
 * context around each spec and autowires the beans a spec declares in its constructor. Without it
 * they are inert and every spec fails on a missing bean, which is a confusing way to learn this file
 * was never written.
 *
 * **`extensions` is `final`, and that is the point.** The failure this exists to prevent is an
 * application adding an extension of its own, overriding `extensions` to say so, dropping
 * [SpringExtension] on the way and watching the whole suite fail on missing beans. Extra extensions
 * are passed to the constructor instead — `SpringProjectConfig(MyListener())` — and keep it.
 */
abstract class SpringProjectConfig(
    vararg extra: Extension,
) : AbstractProjectConfig() {
    final override val extensions: List<Extension> = listOf(SpringExtension()) + extra
}
