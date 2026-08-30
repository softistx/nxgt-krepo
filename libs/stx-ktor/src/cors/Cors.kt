package com.strange.ktor.cors

import com.strange.common.http.CorsPolicy
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Ktor's CORS plugin, configured from a [CorsPolicy].
 *
 * ```kotlin
 * install(cors(CorsPolicy(origins = listOf("http://localhost:5173"))))
 * ```
 *
 * Or, since the policy is the only argument:
 *
 * ```kotlin
 * cors(policy)
 * ```
 *
 * **The policy is the shared type on purpose.** `stx-spring-boot` builds Spring's
 * `CorsConfiguration` from the same one, so an application that moves between the two frameworks
 * keeps its origins, its methods and its configuration keys. CORS is a browser policy, not a
 * web-framework feature.
 *
 * [CorsPolicy.validate] runs first, so a wildcard origin with credentials fails here while the
 * application is starting rather than on somebody's first preflight.
 *
 * Two of the policy's fields have no Ktor equivalent and are ignored: `path`, because Ktor scopes a
 * plugin by installing it on a route rather than by pattern, and `originPatterns` — Ktor matches a
 * host and a scheme rather than a glob, so a pattern is expressed as `allowHost` there. Both are
 * flagged rather than silently dropped: see [ignoredByKtor].
 */
fun Application.cors(policy: CorsPolicy) {
    policy.validate(patternsSetting = "CorsPolicy.originPatterns")
    install(CORS) {
        if (CorsPolicy.WILDCARD in policy.origins) {
            anyHost()
        } else {
            policy.origins.forEach { allowHost(it.substringAfter("://"), schemes = listOf(it.substringBefore("://"))) }
        }

        if (CorsPolicy.WILDCARD in policy.methods) {
            HttpMethod.DefaultMethods.forEach { allowMethod(it) }
        } else {
            policy.methods.forEach { allowMethod(HttpMethod.parse(it)) }
        }

        if (CorsPolicy.WILDCARD in policy.headers) allowHeaders { true } else policy.headers.forEach { allowHeader(it) }

        policy.exposedHeaders.forEach { exposeHeader(it) }
        allowCredentials = policy.allowCredentials
        maxAgeInSeconds = policy.maxAgeSeconds
    }
}

/**
 * What [cors] cannot express, so a caller reads it here rather than wondering why a field had no
 * effect.
 *
 * A configuration field that a framework quietly ignores is worse than one it refuses: nothing
 * fails, and the policy in the file is not the policy in force.
 */
val CorsPolicy.ignoredByKtor: List<String>
    get() =
        buildList {
            if (originPatterns.isNotEmpty()) add("originPatterns")
            if (path != CorsPolicy().path) add("path")
        }
