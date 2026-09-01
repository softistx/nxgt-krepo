package com.softistx.mongo.query

import com.mongodb.kotlin.client.coroutine.ClientSession

/**
 * The driver overloads every operation on whether a session is passed, but has no overload for a
 * session that *might* be there — which is the shape a service actually has, since the same
 * repository method is called inside a transaction and outside one.
 *
 * Every extension in this package takes `session: ClientSession? = null` last and routes through
 * here, so moving a call into a transaction is threading one value through instead of switching to
 * a different API.
 */
internal inline fun <R> ClientSession?.select(
    inSession: (ClientSession) -> R,
    orElse: () -> R,
): R = if (this != null) inSession(this) else orElse()
