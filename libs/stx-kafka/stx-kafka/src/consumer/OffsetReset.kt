package com.softistx.kafka.consumer

/**
 * Where a group starts when it has no committed offset — a brand new group, or one whose offsets
 * have aged out of `__consumer_offsets`.
 *
 * It applies *only* then. A group that has committed once reads from where it left off, and no
 * amount of [Earliest] will make it re-read what it already acknowledged.
 */
enum class OffsetReset(
    internal val value: String,
) {
    /** From the beginning of the topic. What a new consumer of an event log usually wants. */
    Earliest("earliest"),

    /** From now. Everything written before this consumer existed is skipped. */
    Latest("latest"),

    /** Refuse to guess: the consumer fails rather than silently choosing a starting point. */
    None("none"),
}
