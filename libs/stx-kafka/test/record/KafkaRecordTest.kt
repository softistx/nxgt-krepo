package com.softistx.kafka.record

import com.softistx.kafka.KafkaValueException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.kafka.clients.consumer.ConsumerRecord
import kotlin.time.Instant

/**
 * The consumer hands out this type rather than Kafka's `ConsumerRecord`, so what the mapping keeps
 * is what a caller can act on — losing the offset here would lose the ability to acknowledge,
 * redeliver or dead-letter the record it names.
 */
class KafkaRecordTest :
    FeatureSpec({

        feature("mapping a consumed record") {
            scenario("it keeps where the record came from, not only what was in it") {
                val consumed =
                    ConsumerRecord("orders", 2, 17L, 1_700_000_000_000L, null, 0, 0, "k1", "v1", headersOf("hop" to "a").toKafka(), null)

                val record = KafkaRecord.from(consumed)

                record.topic shouldBe "orders"
                record.partition shouldBe 2
                record.offset shouldBe 17L
                record.timestamp shouldBe Instant.fromEpochMilliseconds(1_700_000_000_000L)
                record.key shouldBe "k1"
                record.value shouldBe "v1"
                record.headers["hop"] shouldBe "a"
            }
        }

        feature("a record with no value") {
            scenario("it stays null, because on a compacted topic that is a deletion") {
                val tombstone = KafkaRecord<String, String>("orders", 0, 3L, Instant.fromEpochMilliseconds(0), "k1", null)

                tombstone.value shouldBe null
            }

            scenario("requireValue is the version for topics that have no tombstones, and it names the record") {
                val tombstone = KafkaRecord<String, String>("orders", 0, 3L, Instant.fromEpochMilliseconds(0), "k1", null)

                val failure = shouldThrow<KafkaValueException> { tombstone.requireValue() }

                failure.message shouldContain "orders-0 offset 3"
            }
        }
    })
