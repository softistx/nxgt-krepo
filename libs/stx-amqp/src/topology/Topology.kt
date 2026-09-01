package com.softistx.amqp.topology

import com.softistx.amqp.Amqp
import kotlin.time.Duration

/**
 * What was declared — kept so a caller can see it, and a test can take it down again.
 */
data class Topology(
    val exchanges: List<Exchange> = emptyList(),
    val queues: List<Queue> = emptyList(),
    val bindings: List<Binding> = emptyList(),
)

/**
 * Declares exchanges, queues and the bindings between them, in the order the broker needs.
 *
 * ```kotlin
 * amqp.declare {
 *     exchange("orders")
 *     exchange("orders.dead", ExchangeType.Fanout)
 *
 *     queue("billing") {
 *         deadLetterTo("orders.dead")
 *         bindTo("orders", "order.placed", "order.cancelled")
 *     }
 *     queue("billing.dead") { bindTo("orders.dead") }
 * }
 * ```
 *
 * **Order in the block does not matter.** Every exchange is declared, then every queue, then every
 * binding, because a binding needs both ends to exist and the alternative is a file whose lines
 * have to be sorted by hand — the kind of ordering that works until someone adds a queue in the
 * obvious place.
 *
 * **Declaring is idempotent, and only while nothing changed.** Re-declaring the queue that is
 * already there is success and is exactly what an application should do on every start; declaring
 * one that *disagrees* with what is there fails and closes the channel it was declared on. See
 * [Queue] for why that is the right behaviour and not an inconvenience.
 *
 * All of it goes over one borrowed channel, so a topology is one round of work rather than one per
 * line.
 */
suspend fun Amqp.declare(block: TopologyScope.() -> Unit): Topology {
    val topology = TopologyScope().apply(block).build()
    withChannel { channel ->
        topology.exchanges.forEach { exchange ->
            channel.exchangeDeclare(
                exchange.name,
                exchange.type.value,
                exchange.durable,
                exchange.autoDelete,
                exchange.arguments,
            )
        }
        topology.queues.forEach { queue ->
            channel.queueDeclare(
                queue.name,
                queue.durable,
                queue.exclusive,
                queue.autoDelete,
                queue.asArguments(),
            )
        }
        topology.bindings.forEach { binding ->
            channel.queueBind(binding.queue, binding.exchange, binding.routingKey, binding.arguments)
        }
    }
    return topology
}

/** Declares one queue and its bindings — [declare] for the common case of a single consumer. */
suspend fun Amqp.declareQueue(
    name: String,
    type: QueueType = QueueType.Classic,
    durable: Boolean = true,
    exclusive: Boolean = false,
    autoDelete: Boolean = false,
    deadLetter: DeadLetter? = null,
    messageTtl: Duration? = null,
    maxLength: Long? = null,
    arguments: Map<String, Any> = emptyMap(),
): Queue =
    Queue(name, type, durable, exclusive, autoDelete, deadLetter, messageTtl, maxLength, arguments)
        .also { queue -> declare { queue(queue) } }

/** The receiver of the [declare] block. */
class TopologyScope internal constructor() {
    private val exchanges = mutableListOf<Exchange>()
    private val queues = mutableListOf<Queue>()
    private val bindings = mutableListOf<Binding>()

    fun exchange(
        name: String,
        type: ExchangeType = ExchangeType.Topic,
        durable: Boolean = true,
        autoDelete: Boolean = false,
        arguments: Map<String, Any> = emptyMap(),
    ): Exchange = Exchange(name, type, durable, autoDelete, arguments).also { exchanges += it }

    fun exchange(exchange: Exchange): Exchange = exchange.also { exchanges += it }

    fun queue(queue: Queue): Queue = queue.also { queues += it }

    /**
     * A queue, and inside the block the things that only make sense next to it: what it dead-letters
     * to, and what it is bound to.
     */
    fun queue(
        name: String,
        type: QueueType = QueueType.Classic,
        durable: Boolean = true,
        block: QueueScope.() -> Unit = {},
    ): Queue {
        val scope = QueueScope(name, type, durable).apply(block)
        bindings += scope.bindings
        return scope.build().also { queues += it }
    }

    /** A binding for a queue declared elsewhere — including one this application does not own. */
    fun bind(
        queue: String,
        exchange: String,
        routingKey: String = "",
        arguments: Map<String, Any> = emptyMap(),
    ): Binding = Binding(queue, exchange, routingKey, arguments).also { bindings += it }

    internal fun build() = Topology(exchanges.toList(), queues.toList(), bindings.toList())
}

/** The receiver of a [TopologyScope.queue] block. */
class QueueScope internal constructor(
    private val name: String,
    private val type: QueueType,
    private val durable: Boolean,
) {
    internal val bindings = mutableListOf<Binding>()

    var exclusive: Boolean = false
    var autoDelete: Boolean = false
    var messageTtl: Duration? = null
    var maxLength: Long? = null
    var arguments: Map<String, Any> = emptyMap()

    private var deadLetter: DeadLetter? = null

    /** Where this queue's rejected, expired and overflowing messages go. */
    fun deadLetterTo(
        exchange: String,
        routingKey: String? = null,
    ) {
        deadLetter = DeadLetter(exchange, routingKey)
    }

    /**
     * Binds this queue to [exchange] for each routing key.
     *
     * No keys at all binds with an empty one, which is what a fanout exchange wants and what a
     * topic exchange treats as "the key is literally empty" — so a topic binding names its keys.
     */
    fun bindTo(
        exchange: String,
        vararg routingKeys: String,
        arguments: Map<String, Any> = emptyMap(),
    ) {
        if (routingKeys.isEmpty()) {
            bindings += Binding(name, exchange, "", arguments)
        } else {
            routingKeys.forEach { key -> bindings += Binding(name, exchange, key, arguments) }
        }
    }

    internal fun build() = Queue(name, type, durable, exclusive, autoDelete, deadLetter, messageTtl, maxLength, arguments)
}
