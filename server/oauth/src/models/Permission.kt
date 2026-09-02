package com.softistx.oauth.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Permission(
    @SerialName("_id")
    val id: String,
    val name: String,
    val description: String?,
)
