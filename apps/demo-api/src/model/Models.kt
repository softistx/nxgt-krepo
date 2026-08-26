package com.strange.demo.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * The slice of `openapi.yaml` this demo server implements.
 *
 * These are written by hand rather than generated: the spec is the contract between the two
 * sides, and the demo only proves something if the server reaches it independently of the
 * client generator. A mismatch here should surface as a failing end-to-end test.
 */
@Serializable
public data class AuditMetadata(
    public val createdBy: String,
    public val createdDate: Instant,
    public val lastModifiedBy: String,
    public val lastModifiedDate: Instant,
)

@Serializable
public data class Category(
    public val id: String,
    public val name: String,
    public val family: String? = null,
    public val description: String? = null,
    public val photo: String? = null,
    public val attributes: List<String>? = null,
    public val metadata: AuditMetadata,
)

@Serializable
public data class CategoryRequest(
    public val name: String,
    public val family: String? = null,
    public val description: String? = null,
    public val attributes: List<String>? = null,
)

@Serializable
public data class Tag(
    public val id: String,
    public val name: String,
    public val family: String? = null,
    public val description: String? = null,
    public val metadata: AuditMetadata,
)

@Serializable
public data class TagRequest(
    public val name: String,
    public val family: String? = null,
    public val description: String? = null,
)

@Serializable
public data class PatchTagRequest(
    public val name: String? = null,
    public val family: String? = null,
    public val description: String? = null,
)

@Serializable
public data class SearchRequest(
    public val filter: JsonObject? = null,
    public val sort: JsonObject? = null,
)

@Serializable
public data class PageInfo(
    public val startCursor: String? = null,
    public val endCursor: String? = null,
    public val hasPreviousPage: Boolean? = null,
    public val hasNextPage: Boolean? = null,
)

@Serializable
public data class PaginatedCategory(
    public val `data`: List<Category>? = null,
    public val metadata: PageInfo? = null,
)

@Serializable
public data class PaginatedTag(
    public val `data`: List<Tag>? = null,
    public val metadata: PageInfo? = null,
)

@Serializable
public data class ErrorResponse(
    public val status: Int,
    public val message: String = "",
    public val debugMessage: String? = null,
)
