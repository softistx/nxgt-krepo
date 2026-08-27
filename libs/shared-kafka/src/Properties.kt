package com.strange.kafka

import org.apache.kafka.clients.CommonClientConfigs

/**
 * The property map every client here starts from.
 *
 * Order is the point: what [KafkaConfig.properties] says wins over what this module chose, and what
 * a call site passes wins over both. A caller who has to override something we picked should not
 * have to fork the module to do it.
 */
internal fun KafkaConfig.clientProperties(vararg overrides: Pair<String, Any>): MutableMap<String, Any> {
    val properties = mutableMapOf<String, Any>(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to bootstrap)
    clientId?.let { properties[CommonClientConfigs.CLIENT_ID_CONFIG] = it }
    properties.putAll(this.properties)
    properties.putAll(overrides)
    return properties
}
