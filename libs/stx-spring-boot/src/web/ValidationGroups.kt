package com.softistx.spring.web

import jakarta.validation.groups.Default

/**
 * The three groups a resource type is usually validated against, so every module does not invent its
 * own names for the same three.
 *
 * ```kotlin
 * data class ProductRequest(
 *     @field:NotBlank(groups = [ValidationGroups.Create::class]) val name: String?,
 *     @field:Positive val price: Int?,
 * )
 * ```
 *
 * [Create] extends `Default`, so validating a create also applies every unqualified constraint —
 * which is what someone writing `@field:Positive` with no group meant. [Update] extends [Create]:
 * a full replacement has to satisfy everything a creation does, or a PUT becomes a way to reach a
 * state a POST refuses. [Patch] deliberately does not, because a partial update sends the fields it
 * is changing and nothing else, and `@NotBlank` on an absent field is not a violation of anything.
 */
interface ValidationGroups {
    interface Create : Default

    interface Update : Create

    interface Patch : Default
}
