package com.softistx.redis.pubsub

/** A message, and the channel it actually arrived on — which a pattern subscriber cannot infer. */
data class TopicMessage<T>(
    val channel: String,
    val value: T,
)
