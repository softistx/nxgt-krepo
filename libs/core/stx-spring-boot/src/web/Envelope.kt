package com.softistx.spring.web

import com.softistx.common.page.Page
import com.softistx.common.page.PageInfo
import kotlinx.serialization.Serializable

/**
 * A body with room beside the data for the things that are *about* the data — where this page sits,
 * and where to go next.
 *
 * ```kotlin
 * suspend fun list(request: ServerRequest) = products.page(request).response().ok()
 * ```
 *
 * **There is no `error` field, and that is the design.** An error never comes back through a route's
 * return value here — it is thrown as an `ApiException` and answered by `ApiExceptionHandler` with
 * an `ErrorResponse`. A `data`/`error` union in one type means every client unwraps two levels to
 * find out something failed, when the status code already said so.
 *
 * Both parameters are `out`, so a `Response<Product, Nothing>` is usable wherever a
 * `Response<Product, PageInfo>` is expected — which is what lets one route return a page and
 * another return a single value through the same declared type.
 *
 * Using it is a route's choice. Nothing in this module returns it for you.
 */
@Serializable
data class Response<out D, out M>(
    val data: D,
    val metadata: M? = null,
    val links: Map<String, String>? = null,
)

/** This value as a body, with no metadata. */
fun <D> D.response(links: Map<String, String>? = null): Response<D, Nothing> = Response(this, links = links)

/**
 * A page as a body: the rows in `data`, the cursors in `metadata`.
 *
 * The two halves separate here rather than at the client, which is the whole reason
 * `com.softistx.common.page.Page` exists — a list that has lost its cursors cannot ask for the next
 * page.
 */
fun <T> Page<T>.response(links: Map<String, String>? = null): Response<List<T>, PageInfo> = Response(data, metadata = info, links = links)
