package com.softistx.redis.stream

import com.softistx.redis.Redis
import com.softistx.redis.RedisValueException
import com.softistx.redis.codec.JsonValueCodec
import com.softistx.redis.codec.ValueCodec
import io.lettuce.core.Consumer
import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.RedisBusyException
import io.lettuce.core.XAddArgs
import io.lettuce.core.XAutoClaimArgs
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import io.lettuce.core.StreamMessage as LettuceStreamMessage

/**
 * An append-only log with consumer groups — the durable half of Redis messaging.
 *
 * Where a topic delivers to whoever is listening and forgets, a stream keeps every entry until it is
 * trimmed, hands each one to exactly one consumer in a group, and remembers that it was handed over
 * until someone acknowledges it. A consumer that dies mid-entry leaves it pending rather than losing
 * it, and [claimStale] is how the next consumer picks it up.
 *
 * ```kotlin
 * val orders = redis.stream<OrderEvent>("orders", maxLength = 100_000)
 * orders.append(OrderEvent.Placed(id))
 * orders.process(group = "billing", consumer = "worker-1") { event -> charge(event) }
 * ```
 *
 * Delivery is **at least once**: [process] acknowledges after the handler returns, so a handler that
 * succeeds and then loses the connection sees its entry again. Handlers here have to be idempotent;
 * acknowledging first would trade that for losing the entry instead, which is the worse half of the
 * same coin.
 */
class RedisStream<T>(
    private val redis: Redis,
    val name: String,
    private val codec: ValueCodec<T>,
    private val maxLength: Long? = null,
) {
    /** The same stream, named by its serializer — for a call site whose `T` cannot be reified. */
    constructor(
        redis: Redis,
        name: String,
        serializer: KSerializer<T>,
        maxLength: Long? = null,
        json: Json = redis.json,
    ) : this(redis, name, JsonValueCodec(json, serializer), maxLength)

    val key: String get() = redis.key("stream", name)

    /** Appends [value] and answers with the id the stream gave it. */
    suspend fun append(value: T): String {
        val body = mapOf(FIELD to codec.encode(value))
        val id =
            if (maxLength == null) {
                redis.commands.xadd(key, body)
            } else {
                /* Approximate trimming: exact means walking the radix tree to a precise length, for
                   a bound nobody chose to the entry. */
                redis.commands.xadd(key, XAddArgs.Builder.maxlen(maxLength).approximateTrimming(), body)
            }
        return id ?: error("XADD to '$key' answered no id")
    }

    suspend fun length(): Long = redis.commands.xlen(key) ?: 0L

    /**
     * Trims to [maxLength] entries and answers with how many went.
     *
     * Approximate by default, which is what Redis is built for: it drops whole macro-nodes and stops
     * at the first one it would have to split, so a trim costs the same whatever the stream's size
     * and the length lands near the bound rather than on it. Pass `approximate = false` when the
     * bound is the point.
     */
    suspend fun trim(
        maxLength: Long,
        approximate: Boolean = true,
    ): Long = redis.commands.xtrim(key, approximate, maxLength) ?: 0L

    /**
     * Creates the group if it is not there, and the stream with it.
     *
     * Idempotent by catching `BUSYGROUP` rather than by asking first: two workers starting together
     * would both be told it does not exist and both try to create it, and the loser needs this
     * anyway. `MKSTREAM` is what lets a consumer start before the first producer.
     *
     * A new group starts at the *end* of the stream — it is a subscription, not a backfill. Read
     * what came before with [history].
     */
    suspend fun createGroup(group: String) {
        try {
            redis.commands.xgroupCreate(
                XReadArgs.StreamOffset.latest(key),
                group,
                XGroupCreateArgs.Builder.mkstream(),
            )
        } catch (e: RedisBusyException) {
            if (e.message?.contains("BUSYGROUP") != true) throw e
        }
    }

    /**
     * Entries for [consumer] in [group], as they arrive, until the collector stops.
     *
     * On a connection of its own, because `XREADGROUP BLOCK` holds one — issued on the shared
     * connection it would stall every other command in the application for the length of the block.
     *
     * [block] is a *poll* interval, not a timeout: the read waits that long for an entry, then the
     * loop comes round and checks whether the flow is still being collected. Blocking forever would
     * be one fewer round trip and a consumer that ignores cancellation until the next entry, which
     * on a quiet stream can be a very long time.
     *
     * Nothing is acknowledged here. Every entry emitted stays pending until [ack], which is what
     * makes redelivery possible; [process] is the version that acknowledges for you.
     */
    fun consume(
        group: String,
        consumer: String,
        block: Duration = 2.seconds,
        count: Long = 10,
    ): Flow<StreamRecord<T>> =
        flow {
            createGroup(group)
            redis.dedicated().use { connection ->
                val commands = connection.coroutines()
                val args = XReadArgs.Builder.block(block.toJavaDuration()).count(count)
                while (currentCoroutineContext().isActive) {
                    commands
                        .xreadgroup(Consumer.from(group, consumer), args, XReadArgs.StreamOffset.lastConsumed(key))
                        .toList()
                        .forEach { emit(it.toRecord()) }
                }
            }
        }

    /**
     * [consume], acknowledging each entry once [handler] returns.
     *
     * A handler that throws does not acknowledge, so the entry stays pending and another consumer
     * can [claimStale] it — the exception still reaches the caller, because a consumer that swallows
     * failures is a queue that quietly stops working.
     */
    suspend fun process(
        group: String,
        consumer: String,
        block: Duration = 2.seconds,
        count: Long = 10,
        handler: suspend (T) -> Unit,
    ) {
        consume(group, consumer, block, count).collect { record ->
            handler(record.value)
            ack(group, record.id)
        }
    }

    /** How many entries [group] was handed and has not acknowledged. */
    suspend fun ack(
        group: String,
        vararg ids: String,
    ): Long = redis.commands.xack(key, group, *ids) ?: 0L

    suspend fun pending(group: String): Long = redis.commands.xpending(key, group)?.count ?: 0L

    /**
     * Takes over entries that have been pending longer than [minIdle] and gives them to [consumer].
     *
     * This is the recovery path a consumer group needs and does not get for free: an entry handed to
     * a worker that then died stays pending forever, because the group has already delivered it. Run
     * this on an interval, with a [minIdle] comfortably longer than a normal handler takes.
     */
    suspend fun claimStale(
        group: String,
        consumer: String,
        minIdle: Duration,
        count: Int = 10,
    ): List<StreamRecord<T>> {
        val args =
            XAutoClaimArgs
                .Builder
                .xautoclaim(Consumer.from(group, consumer), minIdle.toJavaDuration(), "0")
                .count(count.toLong())
        return redis.commands
            .xautoclaim(key, args)
            ?.messages
            .orEmpty()
            .map { it.toRecord() }
    }

    /** The last [count] entries, newest last, regardless of any group. */
    suspend fun history(count: Long = 100): List<StreamRecord<T>> =
        redis.commands
            .xrange(key, Range.unbounded(), Limit.create(0, count))
            .toList()
            .map { it.toRecord() }

    private fun LettuceStreamMessage<String, String>.toRecord(): StreamRecord<T> {
        val raw = body[FIELD] ?: throw RedisValueException("stream entry $id has no '$FIELD' field")
        return StreamRecord(id, codec.decode(raw))
    }

    private companion object {
        /** Streams store hashes; everything here lives in one field, so the value is the entry. */
        const val FIELD = "value"
    }
}

/**
 * A stream of `T`, serialized with kotlinx.serialization through this connection's `Json`.
 *
 * ```kotlin
 * val orders = redis.stream<OrderEvent>("orders", maxLength = 100_000)
 * ```
 *
 * [maxLength] is the bound every [RedisStream.append] trims to, approximately — without one the
 * stream keeps every entry ever appended, which is a decision rather than a default.
 */
inline fun <reified T> Redis.stream(
    name: String,
    maxLength: Long? = null,
    json: Json = this.json,
): RedisStream<T> = RedisStream(this, name, ValueCodec.json<T>(json), maxLength)
