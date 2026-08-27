package com.strange.redis.stream

/**
 * One entry, and the id the stream gave it.
 *
 * The id is not decoration: it is what an acknowledgement names, so a consumer that keeps the value
 * and drops the id has kept the half it cannot act on. Named `StreamRecord` rather than
 * `StreamMessage` because Lettuce already has a `StreamMessage`, and one of them appearing in an
 * import list where the other was meant is not a mistake worth making possible.
 */
data class StreamRecord<T>(
    val id: String,
    val value: T,
)
