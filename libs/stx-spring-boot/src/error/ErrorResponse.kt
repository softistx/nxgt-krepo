package com.softistx.spring.error

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
 *
 * **[timestamp] is a `String`, and deliberately.** This type is serialized by whichever codec the
 * application installed, and the two candidates do not agree about `kotlin.time.Instant`: Jackson —
 * which is what WebFlux uses until something replaces it — has never heard of it and writes it as
 * `{"epochSeconds":…,"nanosecondsOfSecond":…}`, while kotlinx writes ISO-8601. A response body whose
 * shape depends on a codec somebody may or may not have configured is not a contract, so the ISO-8601
 * text is stored rather than derived. [instant] parses it back for a caller who wants the value.
 */
@Serializable
data class ErrorResponse(
    val message: String,
    val status: String,
    val code: String? = null,
    val timestamp: String = Clock.System.now().toString(),
    val debugMessage: String? = null,
) {
    /** [timestamp] as the value it spells. */
    val instant: Instant get() = Instant.parse(timestamp)
}
