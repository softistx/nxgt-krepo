package com.softistx.oauth.plugins

import com.softistx.oauth.di.KtorApplication
import com.strange.api.di.Properties
import io.ktor.server.application.*
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.environmentProperties
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.koin.plugin.module.dsl.withConfiguration

@OptIn(KoinExperimentalAPI::class)
fun Application.configureDependencyInjection() {
    install(Koin) {
        environmentProperties()
        properties(mapOf(Properties.ENVIRONMENT to environment, Properties.APPLICATION to this))
        slf4jLogger(level = Level.INFO)
        bridge {
            koinToKtor()
            ktorToKoin()
        }
        modules(
            module {
            },
        )
        withConfiguration<KtorApplication>()
    }
}
