package com.strange.material.models

import kotlinx.serialization.json.JsonObject

data class QueryFilter(
    val page: Int? = null,
    val size: Int? = null,
    val filter: JsonObject? = null,
    val sort: JsonObject? = null,
)
