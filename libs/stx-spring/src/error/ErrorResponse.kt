package com.strange.spring.error

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What a failure looks like on the wire — one shape for every error this service returns.
 *
 * [message] is already translated: the key is resolved against the requesting locale's catalogs
 * before this is built, so a client displays it as-is. [code] is the untranslated key, and it is
 * what a client should branch on — text changes when someone improves a sentence, and a client
 * matching on text breaks the day that happens.
 *
 * [debugMessage] is absent unless `stx.errors.include-debug-message` is on. It carries whatever the
 * thrower thought a developer would want, which is routinely a query, a constraint name or an
 * upstream body — none of which belongs in a response a stranger can read.
 */
@Serializable
data class ErrorResponse(
    val message: String,
    val status: String,
    val code: String? = null,
    val timestamp: Instant = Clock.System.now(),
    val debugMessage: String? = null,
)
