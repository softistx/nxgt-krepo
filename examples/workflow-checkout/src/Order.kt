package com.softistx.example.workflow

import kotlinx.serialization.Serializable

/**
 * The workflow's context: everything one checkout knows about itself.
 *
 * Every field has a default, which is the rule a persisted context lives by — an instance written by
 * one deploy has to decode under the next one. The handles a step earns are nullable because they do
 * not exist until that step has run, and a compensation reads them back to know what to undo.
 */
@Serializable
data class Order(
    val id: String,
    val items: List<String>,
    val total: Int,
    val card: String,
    /** True for a customer who paid for next-day delivery — the branch turns on this. */
    val express: Boolean = false,
    val reservationId: String? = null,
    val chargeId: String? = null,
    val trackingId: String? = null,
)
