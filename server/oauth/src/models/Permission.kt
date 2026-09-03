package com.softistx.oauth.models

import com.softistx.oauth.graphql.types.AuditMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class Permission(
    @SerialName("_id")
    val id: String,
    val name: String,
    val description: String?,
    val metadata: AuditMetadata = AuditMetadata(Clock.System.now(), Clock.System.now()),
)
