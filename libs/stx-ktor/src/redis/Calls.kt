package com.softistx.ktor.redis

import com.softistx.ktor.required
import com.softistx.redis.Redis
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's Redis connection, as [RedisConnection] opened it. */
val Application.redis: Redis get() = required(RedisKey, "RedisConnection")

/** The same connection, from a route. It is shared: this is not a per-request connection. */
val ApplicationCall.redis: Redis get() = application.redis
