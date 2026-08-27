package com.strange.mongo.page

import kotlinx.serialization.Serializable

/**
 * Where this page sits in the result set, in the shape the Relay connection spec uses.
 *
 * [hasPreviousPage] on a forward page is `true` whenever the caller paged into it, and
 * [hasNextPage] on a backward page likewise: knowing for certain costs a second query in the other
 * direction, and no caller has ever needed the certainty enough to pay for it. The spec allows
 * exactly this.
 */
@Serializable
data class PageInfo(
    val startCursor: String? = null,
    val endCursor: String? = null,
    val hasPreviousPage: Boolean = false,
    val hasNextPage: Boolean = false,
)
