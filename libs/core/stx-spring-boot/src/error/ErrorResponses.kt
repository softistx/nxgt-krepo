package com.softistx.spring.error

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * The one place a status, a code and a message become a response.
 *
 * Every handler in this package goes through it, so the decision that
 * `stx.errors.include-debug-message` governs is made once. A handler that built its own
 * `ResponseEntity` would be one `debugMessage` away from leaking in production, and nothing would
 * fail to say so.
 */
internal fun errorResponse(
    status: HttpStatus,
    code: String,
    message: String,
    debugMessage: String?,
    properties: ErrorProperties,
): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(status).body(
        ErrorResponse(
            message = message,
            status = status.name,
            code = code,
            debugMessage = debugMessage.takeIf { properties.includeDebugMessage },
        ),
    )
