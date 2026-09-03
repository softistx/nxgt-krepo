package com.softistx.graphix.ktor

import com.softistx.graphix.Graphix
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Registers the GraphQL engine the plugin installed with Ktor's DI, without building a second one.
 *
 * Called by [GraphQL] at install — there is nothing to switch on.
 */
internal fun Application.provideGraphix() {
    val engine = graphix
    dependencies {
        provide<Graphix> { engine }
    }
}
