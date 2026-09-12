package com.softistx.migrations.spring

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.migrations.db.mongo.MongoMigration
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.cglib.proxy.Enhancer
import org.springframework.cglib.proxy.MethodInterceptor

/**
 * What a *proxied* migration records as its description.
 *
 * `@MigrationUnit`'s runner had to strip the CGLIB suffix because the class name carried the version
 * — a proxy there did not spoil a label, it lost a migration outright. Declaring the version made
 * that failure impossible, and in doing so made it easy to forget the strip entirely: the symptom
 * shrank to a ledger row reading `V1Proxied$$SpringCGLIB$$0`, which nothing fails on and nobody
 * wants to read.
 *
 * A bean becomes a subclass like this the moment it also carries `@Transactional` or an aspect
 * matches it, which is not exotic for something that writes to a database. The proxy is built here
 * directly rather than through a transaction manager, because what is being asserted is a property
 * of the class name and not of Spring's proxying rules.
 */
class ProxiedDescriptionTest :
    StringSpec({
        "a plain migration is described by its class name" {
            V1Proxied().description shouldBe "V1Proxied"
        }

        "the proxy really is named the way this spec assumes" {
            // Guards the assertion below: a Spring that stopped spelling proxies with `$$` would
            // otherwise make it pass while testing nothing.
            proxied()::class.java.simpleName shouldContain "$$"
        }

        "a CGLIB subclass is described by the class it was made from" {
            proxied().description shouldBe "V1Proxied"
        }
    })

private open class V1Proxied : MongoMigration {
    override val version = 1L

    override suspend fun migrate(context: MongoDatabase) = Unit
}

/** The subclass Spring would build for a migration carrying `@Transactional` or matching an aspect. */
private fun proxied(): MongoMigration =
    Enhancer()
        .apply {
            setSuperclass(V1Proxied::class.java)
            setCallback(MethodInterceptor { target, method, arguments, proxy -> proxy.invokeSuper(target, arguments) })
        }.create() as MongoMigration
