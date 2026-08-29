package com.strange.spring.data.mongo.audit

import com.strange.spring.data.mongo.template.SpringMongo
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.javers.core.JaversBuilder
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.mapping.Document

@Auditable
@Document("orders")
data class Order(
    @Id val id: String,
    val status: String,
    val total: Int,
)

private suspend fun trail(block: suspend (AuditTrail, ReactiveMongoTemplate) -> Unit) =
    SpringMongo.withTemplate { template ->
        block(AuditTrail(AuditStore(template), JaversBuilder.javers().build()), template)
    }

class AuditTrailTest :
    FeatureSpec({

        feature("recording a document's history").config(enabled = SpringMongo.available) {
            scenario("the first commit is INITIAL and carries the whole state") {
                trail { audit, _ ->
                    val entry = audit.commit(Order("1", "NEW", 100), "orders", "alice")

                    entry shouldNotBe null
                    entry!!.type shouldBe CommitType.INITIAL
                    entry.version shouldBe 0
                    entry.author shouldBe "alice"
                    entry.oid shouldBe "1"
                }
            }

            scenario("a later commit is an UPDATE and names what changed") {
                trail { audit, _ ->
                    audit.commit(Order("1", "NEW", 100), "orders", "alice")

                    val entry = audit.commit(Order("1", "PAID", 100), "orders", "bob")!!

                    entry.type shouldBe CommitType.UPDATE
                    entry.version shouldBe 1
                    entry.author shouldBe "bob"
                    entry.changes.map { it.name } shouldBe listOf("status")
                    entry.changes.single().before shouldBe "NEW"
                    entry.changes.single().after shouldBe "PAID"
                    entry.changes.single().type shouldBe PropertyChangeType.PROPERTY_VALUE_CHANGED
                }
            }

            scenario("a save that changed nothing records nothing") {
                // Spring Data emits an AfterSaveEvent for every save, including the ones that wrote
                // the same values back. A trail full of versions that differ in nothing is a trail
                // nobody reads.
                trail { audit, _ ->
                    audit.commit(Order("1", "NEW", 100), "orders", "alice")

                    audit.commit(Order("1", "NEW", 100), "orders", "alice") shouldBe null
                }
            }

            scenario("two documents keep separate histories") {
                trail { audit, _ ->
                    audit.commit(Order("1", "NEW", 100), "orders", null)
                    val other = audit.commit(Order("2", "NEW", 50), "orders", null)!!

                    other.version shouldBe 0
                    other.type shouldBe CommitType.INITIAL
                }
            }

            scenario("the same id in another collection is another history") {
                trail { audit, _ ->
                    audit.commit(Order("1", "NEW", 100), "orders", null)

                    audit.commit(Order("1", "NEW", 100), "quotes", null)!!.type shouldBe CommitType.INITIAL
                }
            }
        }

        feature("closing a history").config(enabled = SpringMongo.available) {
            scenario("a delete is TERMINAL and keeps the last known state") {
                // The question asked of a deletion is nearly always what it was when it went.
                trail { audit, _ ->
                    audit.commit(Order("1", "PAID", 100), "orders", "alice")

                    val entry = audit.terminate("1", "orders", "bob")!!

                    entry.type shouldBe CommitType.TERMINAL
                    entry.version shouldBe 1
                    entry.author shouldBe "bob"
                    entry.state shouldNotBe null
                }
            }

            scenario("a save after a delete is ignored, not stitched on") {
                // A new document reusing an id is a different thing. Continuing the old history
                // would produce a diff between two unrelated objects, presented as a change
                // somebody made.
                trail { audit, _ ->
                    audit.commit(Order("1", "PAID", 100), "orders", null)
                    audit.terminate("1", "orders", null)

                    audit.commit(Order("1", "NEW", 5), "orders", null) shouldBe null
                }
            }

            scenario("deleting something with no history records nothing") {
                trail { audit, _ ->
                    audit.terminate("nobody", "orders", null) shouldBe null
                }
            }

            scenario("terminating twice is not two entries") {
                trail { audit, _ ->
                    audit.commit(Order("1", "PAID", 100), "orders", null)
                    audit.terminate("1", "orders", null)

                    audit.terminate("1", "orders", null) shouldBe null
                }
            }
        }
    })
