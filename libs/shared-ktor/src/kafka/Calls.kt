package com.strange.ktor.kafka

import com.strange.kafka.Kafka
import com.strange.ktor.required
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's cluster, as [KafkaCluster] configured it. */
val Application.kafka: Kafka get() = required(KafkaKey, "KafkaCluster")

/** The same cluster, from a route. Nothing is open yet: what you build from it, you close. */
val ApplicationCall.kafka: Kafka get() = application.kafka
