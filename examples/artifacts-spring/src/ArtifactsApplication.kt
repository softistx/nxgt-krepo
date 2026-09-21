package com.softistx.example.artifacts.spring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * An application with no code in it, which is the point.
 *
 * `stx-amqp-spring`, `stx-kafka-spring` and `stx-storage-spring` register themselves through
 * `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Spring reads
 * those from the classpath, so the only way to find out whether the file survived publication —
 * and whether the class it names loads — is to start a context that has the published jars on it.
 * `ArtifactsApplicationTest` does that; this class is what it starts.
 *
 * Every one of the three is behind `@ConditionalOnProperty(stx.<name>.enabled)` and none is turned
 * on here. A broker, a cluster and an object store are not needed to prove a POM.
 */
@SpringBootApplication
class ArtifactsApplication

fun main(args: Array<String>) {
    runApplication<ArtifactsApplication>(*args)
}
