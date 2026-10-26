package com.softistx.ktor.cors

import com.softistx.common.http.CorsPolicy
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Ktor's CORS plugin, configured from a [CorsPolicy].
 *
 * ```kotlin
 * fun Application.module() {
 *     cors(CorsPolicy(origins = listOf("http://localhost:5173")))
 * }
 * ```
 *
 * It installs the plugin itself, so it is called inside an `Application` and never wrapped in
 * `install(...)`.
 *
 * **The policy is the shared type on purpose.** `stx-spring-boot` builds Spring's
 * `CorsConfiguration` from the same one, so an application that moves between the two frameworks
 * keeps its origins, its methods and its configuration keys. CORS is a browser policy, not a
 * web-framework feature.
 *
 * [CorsPolicy.validate] runs first, so a wildcard origin with credentials fails here while the
 * application is starting rather than on somebody's first preflight.
 *
 * Each entry of [CorsPolicy.origins] is a full origin, `scheme://host[:port]`; one without the
 * scheme would match nothing in a browser, so it fails here rather than silently. Each entry of
 * [CorsPolicy.originPatterns] is a glob where `*` stands for any run of characters
 * (a scheme, then `*.example.com`), tested against the request's `Origin` header in full.
 *
 * One field has no Ktor equivalent and is ignored: `path`, because Ktor scopes a plugin by installing
 * it on a route rather than by pattern. It is flagged rather than silently dropped: see
 * [ignoredByKtor].
 */
fun Application.cors(policy: CorsPolicy) {
    policy.validate(patternsSetting = "CorsPolicy.originPatterns")
    policy.origins.filter { it != CorsPolicy.WILDCARD }.forEach {
        require("://" in it) {
            "CORS: origin \"$it\" has no scheme — write it as scheme://host[:port], " +
                "for example \"https://$it\". A browser sends the scheme, so this would match nothing."
        }
    }
    val patterns = policy.originPatterns.map(::originPattern)
    install(CORS) {
        if (CorsPolicy.WILDCARD in policy.origins) {
            anyHost()
        } else {
            policy.origins.forEach { allowHost(it.substringAfter("://"), schemes = listOf(it.substringBefore("://"))) }
        }
        if (patterns.isNotEmpty()) allowOrigins { origin -> patterns.any { it.matches(origin) } }

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
            if (path != CorsPolicy().path) add("path")
        }

private fun originPattern(glob: String): Regex = Regex(glob.split("*").joinToString(".*") { Regex.escape(it) })
