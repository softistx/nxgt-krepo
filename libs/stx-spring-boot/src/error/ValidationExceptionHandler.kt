package com.softistx.spring.error

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Bean Validation failures, in the same [ErrorResponse] shape as everything else.
 *
 * **A separate class from [ApiExceptionHandler], and not a second method on it.** Jakarta Validation
 * is a `compile-only` dependency of this module, so an application that never validates anything
 * does not carry it — and a `@RestControllerAdvice` naming `ConstraintViolationException` in a
 * method signature cannot even be loaded when the class is absent. Splitting it lets
 * [ErrorAutoConfiguration] guard this one with `@ConditionalOnClass` and register the other
 * regardless.
 *
 * The violations are joined rather than translated. Each already carries the message its constraint
 * chose, and each names the property path that tells a client which field was rejected — losing the
 * paths to gain a translation is the wrong trade for a form.
 */
@RestControllerAdvice
class ValidationExceptionHandler(
    private val properties: ErrorProperties,
) {
    @ExceptionHandler(ConstraintViolationException::class)
    fun handle(failure: ConstraintViolationException): ResponseEntity<ErrorResponse> =
        errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = CODE,
            message = failure.constraintViolations.joinToString(", ") { "${it.propertyPath}: ${it.message}" },
            debugMessage = null,
            properties = properties,
        )

    companion object {
        /** What a client branches on. The message beside it names the fields and cannot be matched. */
        const val CODE = "errors.validation-failed"
    }
}
