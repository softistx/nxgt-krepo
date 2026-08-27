package com.strange.ktor.amqp

import com.strange.amqp.Amqp
import com.strange.ktor.required
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's AMQP connection, as [AmqpPlugin] opened it. */
val Application.amqp: Amqp get() = required(AmqpKey, "AmqpPlugin")

/**
 * The same connection, from a route.
 *
 * Shared, and safe to be: a connection multiplexes. The channel a route opens over it is not, and
 * belongs to that route.
 */
val ApplicationCall.amqp: Amqp get() = application.amqp
