package com.softistx.kafka.consumer

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition

/**
 * The rule this enforces is the one place concurrent handling can lose data: a committed offset
 * claims everything below it is done, so committing past a record still in flight throws that
 * record away on the next crash. Every scenario here is a shape that has caused exactly that in
 * consumers that parallelised without tracking.
 */
class OffsetTrackerTest :
    FeatureSpec({

        val orders0 = TopicPartition("orders", 0)
        val orders1 = TopicPartition("orders", 1)

        fun offsets(vararg pairs: Pair<TopicPartition, Long>) = pairs.associate { it.first to OffsetAndMetadata(it.second) }

        feature("records that finish in order") {
            scenario("the committable offset is one past the last of them") {
                val tracker = OffsetTracker()

                tracker.completed(orders0, 0)
                tracker.completed(orders0, 1)
                tracker.completed(orders0, 2)

                tracker.committable() shouldBe offsets(orders0 to 3L)
            }
        }

        feature("records that finish out of order") {
            scenario("a gap holds the commit back until it closes") {
                val tracker = OffsetTracker()

                tracker.completed(orders0, 5)
                tracker.completed(orders0, 7)

                // 6 is still running: committing 8 here would lose it on a crash.
                tracker.committable() shouldBe offsets(orders0 to 6L)

                tracker.completed(orders0, 6)

                tracker.committable() shouldBe offsets(orders0 to 8L)
            }

            scenario("a partition nobody has finished anything on contributes nothing") {
                val tracker = OffsetTracker()

                tracker.hasPending() shouldBe false
                tracker.committable() shouldBe emptyMap()
            }
        }

        feature("several partitions at once") {
            scenario("each keeps its own prefix, because ordering is per partition") {
                val tracker = OffsetTracker()

                tracker.completed(orders0, 10)
                tracker.completed(orders1, 4)
                tracker.completed(orders1, 5)

                tracker.committable() shouldBe offsets(orders0 to 11L, orders1 to 6L)
            }
        }

        feature("after a commit") {
            scenario("what was committed is not offered again, and later records still are") {
                val tracker = OffsetTracker()
                tracker.completed(orders0, 0)

                val committed = tracker.committable()
                tracker.committed(committed)

                tracker.committable() shouldBe emptyMap()
                tracker.hasPending() shouldBe false

                tracker.completed(orders0, 1)

                tracker.committable() shouldBe offsets(orders0 to 2L)
            }
        }

        feature("after a rebalance") {
            scenario("a revoked partition is forgotten, and the ones kept are not") {
                val tracker = OffsetTracker()
                tracker.completed(orders0, 3)
                tracker.completed(orders1, 8)

                tracker.forget(listOf(orders1))

                tracker.committable() shouldBe offsets(orders0 to 4L)
            }
        }
    })
