package com.softistx.material.models

/**
 * A request's outcome, as one value: the payload, the reason it failed, or the fact it is still in
 * flight.
 *
 * The three factories are the way to build one. Constructing it directly is possible and rarely
 * what is meant — the primary constructor can express `data` *and* `error` at once, which no
 * request ever produces.
 */
data class ApiResponse<T>(
    val data: T? = null,
    val error: String? = null,
    val loading: Boolean = false,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(data = data)

        fun <T> error(error: String): ApiResponse<T> = ApiResponse(error = error)

        /**
         * Still in flight.
         *
         * It takes nothing, unlike [success] and [error], because there is nothing to carry: the
         * only thing `loading(false)` could mean is a response that is not loading, holds no data
         * and reports no failure, which is not a state a request reaches.
         */
        fun <T> loading(): ApiResponse<T> = ApiResponse(loading = true)
    }
}
