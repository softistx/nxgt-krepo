package com.strange.spring.security

import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize

/**
 * `@RequireRole("ADMIN")` instead of `@PreAuthorize("hasRole('ADMIN')")`.
 *
 * The `{value}` is Spring Security's meta-annotation templating, which resolves only when an
 * `AnnotationTemplateExpressionDefaults` bean is present — `SecurityAutoConfiguration` here
 * registers one when `stx.security.enabled` is true. Without it the expression is the literal text
 * `hasRole('{value}')` and every call is denied, so the spec beside this asserts the bean rather
 * than trusting the annotation.
 *
 * **`@PreAuthorize`, not `@PostAuthorize`.** The version this came from used the latter, which runs
 * the method *first* and denies afterwards — so a caller without the role still got their write
 * performed, and only the response was refused. A check on who may call something has to happen
 * before the something.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasRole('{value}')")
annotation class RequireRole(
    val value: String,
)

/** [RequireRole] for an authority that is not a role — a permission, a scope, a group. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('{value}')")
annotation class RequireAuthority(
    val value: String,
)

/**
 * The caller may have this back only if they created it.
 *
 * `@PostAuthorize` genuinely belongs here, unlike on [RequireRole]: whether the caller owns the
 * thing cannot be known until the thing has been loaded. A read is safe to run and then refuse; put
 * this on a delete and the delete happens.
 *
 * The expression reads `returnObject.metadata.createdBy`, which is the shape `stx-mongo`'s `Audited`
 * gives a document. Nothing here depends on that module — SpEL resolves the path at runtime — so any
 * type with the same two properties works.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PostAuthorize("returnObject.metadata.createdBy == authentication.name")
annotation class RequireOwnership

/**
 * The collection comes back holding only what the caller created.
 *
 * Filtering, not refusing: a list the caller partly owns answers with their part rather than a 403.
 *
 * **This filters what a query already returned**, so the database still read every row and the page
 * a caller asked for can come back short. A query that says `createdBy = me` is the better answer
 * wherever one can be written; this is for the places one cannot.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PostFilter("filterObject.metadata.createdBy == authentication.name")
annotation class FilterByOwnership
