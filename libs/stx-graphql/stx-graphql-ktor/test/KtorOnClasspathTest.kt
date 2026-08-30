package com.strange.graphql.ktor

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.server.application.Application

/**
 * The Ktor integration is empty this slice — no plugin yet — but the module has to compile
 * against `ktor-server-core` so later slices only add sources.
 */
class KtorOnClasspathTest :
    FeatureSpec({
        feature("ktor") {
            scenario("ktor-server-core is on the module classpath") {
                Application::class.java.shouldNotBeNull()
            }
        }
    })
