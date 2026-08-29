package com.strange.spring.error

import org.springframework.http.HttpStatus

/**
 * A failure a client is meant to see, carrying the status it should arrive as.
 *
 * ```kotlin
 * throw ApiException.notFound("orders.not-found", mapOf("id" to id))
 * ```
 *
 * **The message is a translation key, not a sentence.** `ApiExceptionHandler` looks it up in the
 * catalogs for the requesting locale, so the text a client reads is chosen at the boundary and the
 * service that threw does not have to know which language anyone is reading. A key that no catalog
 * answers comes back as the key itself, which is ugly in exactly the place someone will notice —
 * see `MissingKey` in `stx-i18n` for turning that into a failure instead.
 *
 * [args] are the named arguments for that message: `{id}`, `{count}`. Positional arguments are not
 * offered on purpose — `{0}` survives a translator reordering a sentence only by luck, and ICU's
 * plural and select forms read as nonsense with numbers for names.
 *
 * [debugMessage] is for the developer and [code] for the client's own branching. Whether the first
 * one is serialized at all is a deployment's decision, not this type's: see
 * `stx.errors.include-debug-message`.
 */
class ApiException(
    message: String = KEY_UNEXPECTED,
    val status: HttpStatus = HttpStatus.BAD_REQUEST,
    val args: Map<String, Any> = emptyMap(),
    val code: String? = null,
    val debugMessage: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** The key, never null — [RuntimeException.message] is nullable and this one never is. */
    val key: String get() = super.message ?: KEY_UNEXPECTED

    companion object {
        /** What an unhandled failure is reported as. A catalog is expected to answer it. */
        const val KEY_UNEXPECTED = "errors.unexpected"

        fun badRequest(
            message: String,
            args: Map<String, Any> = emptyMap(),
            code: String? = null,
            debugMessage: String? = null,
            cause: Throwable? = null,
        ) = ApiException(message, HttpStatus.BAD_REQUEST, args, code, debugMessage, cause)

        fun unauthorized(
            message: String,
            args: Map<String, Any> = emptyMap(),
            code: String? = null,
            debugMessage: String? = null,
            cause: Throwable? = null,
        ) = ApiException(message, HttpStatus.UNAUTHORIZED, args, code, debugMessage, cause)

        fun forbidden(
            message: String,
            args: Map<String, Any> = emptyMap(),
            code: String? = null,
            debugMessage: String? = null,
            cause: Throwable? = null,
        ) = ApiException(message, HttpStatus.FORBIDDEN, args, code, debugMessage, cause)

        fun notFound(
            message: String,
            args: Map<String, Any> = emptyMap(),
            code: String? = null,
            debugMessage: String? = null,
            cause: Throwable? = null,
        ) = ApiException(message, HttpStatus.NOT_FOUND, args, code, debugMessage, cause)

        fun conflict(
            message: String,
            args: Map<String, Any> = emptyMap(),
            code: String? = null,
            debugMessage: String? = null,
            cause: Throwable? = null,
        ) = ApiException(message, HttpStatus.CONFLICT, args, code, debugMessage, cause)

        fun internal(
            message: String,
            args: Map<String, Any> = emptyMap(),
            code: String? = null,
            debugMessage: String? = null,
            cause: Throwable? = null,
        ) = ApiException(message, HttpStatus.INTERNAL_SERVER_ERROR, args, code, debugMessage, cause)
    }
}
