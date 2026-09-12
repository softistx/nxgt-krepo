package com.softistx.kafka.consumer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.ConcurrentModificationException

/**
 * What "not thread-safe" means for `KafkaConsumer`, established against the real client rather than
 * from memory — the whole shape of [KafkaSubscriber]'s dispatcher rests on the answer.
 *
 * No broker is involved: an unroutable bootstrap address is enough, because everything asked here
 * is answered locally or times out.
 */
class ConsumerConfinementTest :
    FeatureSpec({

        fun consumer() =
            KafkaConsumer(
                mapOf<String, Any>(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:1",
                    ConsumerConfig.GROUP_ID_CONFIG to "confinement-probe",
                    ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG to 2_000,
                ),
                StringDeserializer(),
                StringDeserializer(),
            )

        feature("calling the client from more than one thread") {
            scenario("consecutive calls on different threads are fine — it is not thread-affine") {
                consumer().use { client ->
                    val first = withContext(Dispatchers.IO) { Thread.currentThread().name to client.subscription() }
                    val second =
                        withContext(Dispatchers.IO.limitedParallelism(1)) {
                            Thread.currentThread().name to client.subscription()
                        }

                    first.second shouldBe emptySet()
                    second.second shouldBe emptySet()
                }
            }

            scenario("overlapping calls are what it refuses") {
                consumer().use { client ->
                    coroutineScope {
                        val polling =
                            async(Dispatchers.IO) {
                                client.subscribe(listOf("nothing"))
                                client.poll(java.time.Duration.ofSeconds(2))
                            }

                        delay(300)

                        // A second thread inside the client while the first is still in poll.
                        shouldThrow<ConcurrentModificationException> {
                            withContext(Dispatchers.IO) { client.subscription() }
                        }

                        polling.await()
                    }
                }
            }
        }
    })
