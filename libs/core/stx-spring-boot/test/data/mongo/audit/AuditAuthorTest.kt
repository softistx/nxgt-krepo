package com.softistx.spring.data.mongo.audit

import com.softistx.spring.data.mongo.template.SpringMongo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withTimeout
import org.javers.core.JaversBuilder
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mapping.callback.ReactiveEntityCallbacks
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.User

/** A template whose saves are audited by a real [AuditListener], plus the store to read the trail back. */
private suspend fun audited(block: suspend (ReactiveMongoTemplate, AuditStore) -> Unit) =
    SpringMongo.withTemplate { template ->
        val store = AuditStore(template)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            store.ensureIndexes()
            template.setEntityCallbacks(
                ReactiveEntityCallbacks.create(AuditListener(AuditTrail(store, JaversBuilder.javers().build()), scope)),
            )
            block(template, store)
        } finally {
            scope.cancel()
        }
    }

private suspend fun ReactiveMongoTemplate.saveAs(
    username: String,
    order: Order,
) {
    val user = User(username, "x", emptyList())
    save(order)
        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(UsernamePasswordAuthenticationToken(user, null)))
        .awaitSingle()
}

/** The audit write is asynchronous, so a spec waits for the version it expects. */
private suspend fun AuditStore.awaitVersion(version: Long): AuditEntry =
    withTimeout(10_000) {
        while (true) {
            latest("1", "orders")?.takeIf { it.version >= version }?.let { return@withTimeout it }
            delay(50)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

class AuditAuthorTest :
    FeatureSpec({

        feature("the author of a save").config(enabled = SpringMongo.available) {
            scenario("is the user signed in on the save's own chain") {
                audited { template, store ->
                    template.saveAs("alice", Order("1", "NEW", 100))

                    store.awaitVersion(0).author shouldBe "alice"
                }
            }

            scenario("is empty when nobody is signed in") {
                audited { template, store ->
                    template.save(Order("1", "NEW", 100)).awaitSingle()

                    store.awaitVersion(0).author shouldBe ""
                }
            }
        }

        feature("the unique (oid, collection, version) index").config(enabled = SpringMongo.available) {
            scenario("refuses a second entry at a version already taken") {
                audited { _, store ->
                    store.append(AuditEntry(oid = "9", collection = "orders", version = 0))

                    shouldThrow<DuplicateKeyException> {
                        store.append(AuditEntry(oid = "9", collection = "orders", version = 0))
                    }
                }
            }
        }

        feature("the audit indexes").config(enabled = SpringMongo.available) {
            scenario("AuditIndexes creates the unique index on the configured collection") {
                audited { template, store ->
                    AuditIndexes(store).create()

                    val indexes = template.indexOps(store.audits).indexInfo.collectList().awaitSingle()
                    indexes.single { it.name == "oid_collection_version" }.isUnique shouldBe true
                }
            }
        }

        feature("closing the listener").config(enabled = SpringMongo.available) {
            scenario("lets every queued save land before it returns") {
                SpringMongo.withTemplate { template ->
                    val store = AuditStore(template)
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    val listener = AuditListener(AuditTrail(store, JaversBuilder.javers().build()), scope)
                    template.setEntityCallbacks(ReactiveEntityCallbacks.create(listener))

                    (1..8).forEach { template.save(Order("$it", "NEW", it)).awaitSingle() }
                    listener.close()
                    scope.cancel()

                    template.findAll(AuditEntry::class.java, store.audits).collectList().awaitSingle().size shouldBe 8
                }
            }
        }

        feature("two rapid saves of one document").config(enabled = SpringMongo.available) {
            scenario("land on consecutive versions, in save order, with their own authors") {
                audited { template, store ->
                    template.saveAs("alice", Order("1", "NEW", 100))
                    template.saveAs("bob", Order("1", "PAID", 100))
                    template.saveAs("carol", Order("1", "SHIPPED", 100))

                    val last = store.awaitVersion(2)
                    last.version shouldBe 2
                    last.author shouldBe "carol"
                    store.latest("1", "orders")!!.version shouldBe 2
                }
            }
        }
    })
