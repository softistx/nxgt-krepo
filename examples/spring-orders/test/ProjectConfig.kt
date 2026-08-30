package io.kotest.provided

import com.strange.spring.testing.SpringProjectConfig

/**
 * What lets a Kotest spec here be a Spring test.
 *
 * Kotest looks for this exact class in this exact package, so it cannot live in a library — but what
 * goes in it can, and does: [SpringProjectConfig] registers the `SpringExtension` that makes the
 * annotations on `MongoSpec` mean anything. An extension of this application's own would be passed to
 * the constructor rather than overriding `extensions`, which is how it keeps the Spring one.
 */
object ProjectConfig : SpringProjectConfig()
