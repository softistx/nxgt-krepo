package com.softistx.spring.web

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.springframework.web.reactive.function.server.ServerRequest
import kotlin.reflect.KClass

/**
 * Bean Validation as a statement rather than a result to remember to look at.
 *
 * `Validator.validate` returns a set of violations, and a caller who forgets to check it has written
 * a route that validates nothing and says so nowhere. This throws instead, which
 * `ValidationExceptionHandler` already turns into a 400 naming the fields.
 *
 * In its own file because Jakarta Validation is `compile-only` here: an application that never
 * validates anything does not load this class, and so does not need the library on its classpath.
 */
fun <T : Any> Validator.check(
    value: T,
    vararg groups: KClass<*>,
) {
    val violations = validate(value, *groups.map { it.java }.toTypedArray())
    if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
}

/**
 * The body, decoded and validated before a handler sees it.
 *
 * ```kotlin
 * val product: ProductRequest = request.validBody(validator, ValidationGroups.Create::class)
 * ```
 *
 * The groups are the point of the parameter: a create and a patch validate the same type against
 * different rules, and passing none validates the `Default` group.
 */
suspend inline fun <reified T : Any> ServerRequest.validBody(
    validator: Validator,
    vararg groups: KClass<*>,
): T = body<T>().also { validator.check(it, *groups) }
