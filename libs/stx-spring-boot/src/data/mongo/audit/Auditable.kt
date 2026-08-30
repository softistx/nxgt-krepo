package com.strange.spring.data.mongo.audit

/**
 * A document whose every save and delete is recorded in the audit trail.
 *
 * ```kotlin
 * @Auditable
 * @Document("orders")
 * data class Order(@Id val id: String, val total: BigDecimal, val status: String)
 * ```
 *
 * Opt-in per document, not per application. An audit trail on everything is a second copy of the
 * database that nobody budgeted for, and most collections have nothing worth remembering the history
 * of — this is for the ones where "who changed this, and to what" is a question somebody will ask.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Auditable
