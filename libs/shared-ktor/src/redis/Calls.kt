package com.strange.ktor.redis

import com.strange.ktor.required
import com.strange.redis.Redis
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's Redis connection, as [RedisPlugin] opened it. */
val Application.redis: Redis get() = required(RedisKey, "RedisPlugin")

/** The same connection, from a route. It is shared: this is not a per-request connection. */
val ApplicationCall.redis: Redis get() = application.redis
