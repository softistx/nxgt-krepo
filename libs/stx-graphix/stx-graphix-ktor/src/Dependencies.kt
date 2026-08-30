package com.strange.graphix.ktor

import com.strange.graphix.Graphix
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Makes the GraphQL engine the plugin installed injectable, without building a second one.
 *
 * Off the plugin's default path because `ktor-server-di` is compile-only here. `install(GraphQL) {
 * injectable = true }` is the same call.
 */
fun Application.provideGraphix() {
    val engine = graphix
    dependencies {
        provide<Graphix> { engine }
    }
}
