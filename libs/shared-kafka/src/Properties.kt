package com.strange.kafka

import org.apache.kafka.clients.CommonClientConfigs

/**
 * The property map every client here starts from.
 *
 * Order is the point: [overrides] are what this module chose for a client of this kind,
 * [KafkaConfig.properties] is what the deployment says about every client on this cluster and wins
 * over them, and a call site's own property map — applied by the factory that built these — wins
 * over both. A caller who has to override something we picked should never have to fork the module
 * to do it.
 */
internal fun KafkaConfig.clientProperties(vararg overrides: Pair<String, Any>): MutableMap<String, Any> {
    val properties = mutableMapOf<String, Any>(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to bootstrap)
    clientId?.let { properties[CommonClientConfigs.CLIENT_ID_CONFIG] = it }
    properties.putAll(overrides)
    properties.putAll(this.properties)
    return properties
}
