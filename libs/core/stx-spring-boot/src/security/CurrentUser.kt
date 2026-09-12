package com.softistx.spring.security

import com.softistx.spring.error.ApiException
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails

/**
 * Who is making this request, or null when nobody is.
 *
 * ```kotlin
 * val owner = requireCurrentUser().username
 * ```
 *
 * **`ReactiveSecurityContextHolder` and not `SecurityContextHolder`.** The non-reactive holder is a
 * `ThreadLocal`, and in WebFlux a request is not a thread: a handler resumes on a different worker
 * after any suspension point, and what the thread-local then holds belongs to whichever request last
 * ran there. The reactive one reads from the coroutine's own Reactor context, which travels with the
 * request. This is the same trap `Messages.forRequest` avoids, in the other half of the stack.
 *
 * Returns null rather than throwing on an anonymous request, because an endpoint that is readable by
 * anyone and *richer* when signed in is an ordinary thing to write. [requireCurrentUser] is for the
 * rest.
 */
suspend fun currentUser(): UserDetails? =
    ReactiveSecurityContextHolder
        .getContext()
        .awaitSingleOrNull()
        ?.authentication
        ?.principal as? UserDetails

/**
 * [currentUser], for a route that has nothing to say to a stranger.
 *
 * Fails as `errors.unauthorized` with a 401 — the key, not a sentence, so the text a client reads
 * comes from its own `Accept-Language` like every other failure here.
 */
suspend fun requireCurrentUser(): UserDetails = currentUser() ?: throw ApiException.unauthorized(KEY_UNAUTHORIZED)

/** What a request with no authenticated user is reported as. */
const val KEY_UNAUTHORIZED = "errors.unauthorized"
