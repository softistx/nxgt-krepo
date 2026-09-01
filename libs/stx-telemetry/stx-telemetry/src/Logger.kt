package com.strange.telemetry

import com.strange.telemetry.context.TelemetryContext
import com.strange.telemetry.context.threadContext
import com.strange.telemetry.model.ErrorInfo
import com.strange.telemetry.model.LogRecord
import com.strange.telemetry.model.Severity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.time.Clock

/**
 * Writes logs, attached to whatever span is running.
 *
 * ```kotlin
 * private val log = logger<CheckoutService>()
 *
 * log.info(Charged(order.id, amount))                        // typed
 * log.warn("charge refused", "orderId" to id, "code" to code) // ad hoc
 * log.debug { "state: ${expensive()}" }                       // lazy
 * ```
 *
 * **The typed form is the one to reach for.** The type's serial name is the event's name, its fields
 * are the attributes, and so *deciding what is logged is the same act as declaring a type* — which
 * is the answer to the problem every logging library has with sensitive data. A `toString()` on a
 * domain object logs whatever fields it happens to have, including the one added next quarter,
 * including the card number, and nobody finds out. A `@Serializable` event logs what its author
 * wrote down.
 *
 * `@SerialName("checkout.charged")` is how you name an event; without one the name is the type's
 * qualified name, which is precise and greppable if not pretty.
 *
 * ## None of these functions suspend
 *
 * A log written from an `init` block, from a `catch` in ordinary blocking code, or from a Java
 * callback is a log that still has to come out — so this reads the thread-local mirror that
 * [TelemetryContext] keeps in step with the coroutine context on every dispatch, rather than being
 * suspending and reading the context directly. The mirror is correct because the runtime updates it,
 * which is exactly what an MDC does not have.
 *
 * ## A log is emitted whether or not its trace is sampled
 *
 * Sampling is a decision about the volume of *traces*. A log dropped because its trace was not kept
 * is a log missing at precisely the moment somebody is reading logs to find out what happened — so
 * the `traceId` is attached either way, and an unsampled trace's logs still group together.
 *
 * ## Nothing here can fail
 *
 * No telemetry installed, a severity below the floor, an event that will not serialise: each one is
 * a silent no-op. An application does not fall over because its log line was malformed, and it does
 * not fall over because nobody configured its telemetry.
 */
class Logger
    @PublishedApi
    internal constructor(
        /** Where the log was written from — a class's qualified name, or whatever name was given. */
        val source: String,
    ) {
        fun debug(
            message: String,
            vararg attributes: Pair<String, Any?>,
        ) = write(Severity.Debug, message, attributesOf(*attributes), null)

        fun info(
            message: String,
            vararg attributes: Pair<String, Any?>,
        ) = write(Severity.Info, message, attributesOf(*attributes), null)

        fun warn(
            message: String,
            vararg attributes: Pair<String, Any?>,
        ) = write(Severity.Warn, message, attributesOf(*attributes), null)

        fun error(
            message: String,
            vararg attributes: Pair<String, Any?>,
        ) = write(Severity.Error, message, attributesOf(*attributes), null)

        /** The failure's type, message and — unless the root was built with `stackTraces = false` — its trace. */
        fun warn(
            message: String,
            failure: Throwable,
            vararg attributes: Pair<String, Any?>,
        ) = write(Severity.Warn, message, attributesOf(*attributes), failure)

        fun error(
            message: String,
            failure: Throwable,
            vararg attributes: Pair<String, Any?>,
        ) = write(Severity.Error, message, attributesOf(*attributes), failure)

        /**
         * The lazy form: [message] is not called when nothing would be emitted.
         *
         * It exists for `debug` and `info` alone. Building a warning is not the cost anybody is
         * trying to avoid, and a lazy `error` would only invite a lambda that throws in the middle
         * of handling a failure.
         */
        fun debug(message: () -> String) {
            if (enabled(Severity.Debug)) write(Severity.Debug, message(), Attributes.EMPTY, null)
        }

        fun info(message: () -> String) {
            if (enabled(Severity.Info)) write(Severity.Info, message(), Attributes.EMPTY, null)
        }

        inline fun <reified T : Any> debug(event: T) = event(Severity.Debug, event, serializer(), null)

        inline fun <reified T : Any> info(event: T) = event(Severity.Info, event, serializer(), null)

        inline fun <reified T : Any> warn(event: T) = event(Severity.Warn, event, serializer(), null)

        inline fun <reified T : Any> error(event: T) = event(Severity.Error, event, serializer(), null)

        inline fun <reified T : Any> error(
            event: T,
            failure: Throwable,
        ) = event(Severity.Error, event, serializer(), failure)

        /** True when something is listening and [severity] is at or above the floor. */
        fun enabled(severity: Severity): Boolean = sink()?.let { severity.number >= it.minimum.number } == true

        @PublishedApi
        internal fun <T> event(
            severity: Severity,
            event: T,
            serializer: KSerializer<T>,
            failure: Throwable?,
        ) {
            if (!enabled(severity)) return
            val encoded =
                try {
                    json.encodeToJsonElement(serializer, event)
                } catch (_: SerializationException) {
                    // Better a log with no attributes than an application that fell over writing one.
                    null
                }
            val attributes = (encoded as? JsonObject)?.let { Attributes(it) } ?: Attributes.EMPTY
            write(severity, serializer.descriptor.serialName, attributes, failure)
        }

        private fun write(
            severity: Severity,
            name: String,
            attributes: Attributes,
            failure: Throwable?,
        ) {
            val telemetry = sink() ?: return
            if (severity.number < telemetry.minimum.number) return
            val context = threadContext()
            telemetry.emit(
                LogRecord(
                    at = Clock.System.now(),
                    severity = severity,
                    name = name,
                    source = source,
                    attributes = (context?.attributes ?: Attributes.EMPTY) + attributes,
                    span = context?.span,
                    error = failure?.let { ErrorInfo.of(it, telemetry.stackTraces) },
                ),
            )
        }

        companion object {
            /**
             * The telemetry this call belongs to: the one in scope, else the installed default.
             *
             * That order is the design in one line. The context is asked first, so a spec running
             * under `withTelemetry` collects its own signals however many other specs share the JVM,
             * and the global is only what a caller outside any scope falls back to.
             */
            @PublishedApi
            internal fun sink(): Telemetry? = threadContext()?.telemetry ?: Telemetry.installed

            private val json = Json { encodeDefaults = true }
        }
    }

/** `logger<CheckoutService>()` — named after the class, which is what a reader wants to see. */
inline fun <reified T : Any> logger(): Logger = Logger(T::class.qualifiedName ?: T::class.simpleName ?: "unknown")

/** `logger("outbox")` — for a name that is not a class. */
fun logger(name: String): Logger = Logger(name)
