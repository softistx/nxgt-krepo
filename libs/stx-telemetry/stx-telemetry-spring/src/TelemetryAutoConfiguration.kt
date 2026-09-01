package com.strange.telemetry.spring

import com.strange.telemetry.Telemetry
import com.strange.telemetry.export.ConsoleExporter
import com.strange.telemetry.export.Exporter
import com.strange.telemetry.export.JsonLinesExporter
import com.strange.telemetry.otlp.OtlpExporter
import com.strange.telemetry.trace.Sampler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import kotlin.time.toKotlinDuration

/**
 * One `stx-telemetry` root for the application, and a server span per request.
 *
 * ```yaml
 * spring:
 *   application: { name: checkout }
 * stx:
 *   telemetry:
 *     enabled: true
 *     environment: production
 *     sample-ratio: 0.1
 *     otlp: { enabled: true, endpoint: http://otel-collector:4318 }
 * ```
 *
 * **Opt-in, with no `matchIfMissing`** — the rule every `stx.*` integration in this repository
 * follows. Putting this library on a classpath must not change where an application's logs go.
 *
 * **Every `Exporter` bean is added**, alongside whatever the properties asked for. An application
 * with a destination of its own declares a bean and says nothing else; `stx.telemetry.otlp` is there
 * because the common case should not need a `@Bean` method.
 *
 * The root is [Telemetry.install]ed, which is what makes `logger<T>()` and `span { }` work in a
 * service class with nothing injected — and it is closed by the context, which drains the queue.
 */
@AutoConfiguration
@EnableConfigurationProperties(TelemetryProperties::class, TelemetryOtlpProperties::class)
@ConditionalOnClass(Telemetry::class)
@ConditionalOnProperty(prefix = "stx.telemetry", name = ["enabled"], havingValue = "true")
class TelemetryAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    fun stxTelemetry(
        properties: TelemetryProperties,
        exporters: ObjectProvider<Exporter>,
        environment: Environment,
    ): Telemetry =
        Telemetry(properties.service ?: environment.getProperty("spring.application.name") ?: DEFAULT_SERVICE) {
            version = properties.version
            this.environment = properties.environment
            sampler = Sampler.ratio(properties.sampleRatio)
            minimum = properties.minimum
            stackTraces = properties.stackTraces
            batch = properties.batch
            linger = properties.linger.toKotlinDuration()
            drainTimeout = properties.drainTimeout.toKotlinDuration()
            if (properties.console) export(ConsoleExporter())
            if (properties.jsonLines) export(JsonLinesExporter())
            exporters.orderedStream().forEach(::export)
        }.install()

    /**
     * The server span, as a filter.
     *
     * The bean exists whether or not `web-filter` is set, and the filter itself steps aside when it
     * is false. A `@ConditionalOnProperty(matchIfMissing = true)` would read as an exception to this
     * repository's opt-in rule while meaning something else entirely, and this needs no explaining.
     */
    @Bean
    @ConditionalOnMissingBean
    fun stxTelemetryWebFilter(
        telemetry: Telemetry,
        properties: TelemetryProperties,
    ): TelemetryWebFilter =
        TelemetryWebFilter(
            telemetry = telemetry,
            ignore = if (properties.webFilter) properties.ignore else listOf("/"),
        )

    /**
     * Nested so the `@ConditionalOnClass` applies to this bean alone.
     *
     * On the outer class it would gate the whole configuration, and an application that exports some
     * other way — its own `Exporter` bean, the SLF4J bridge — would silently get no telemetry at all
     * for want of a module it deliberately did not add.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OtlpExporter::class)
    @ConditionalOnProperty(prefix = "stx.telemetry.otlp", name = ["enabled"], havingValue = "true")
    class Otlp {
        @Bean
        @ConditionalOnMissingBean(OtlpExporter::class)
        fun stxOtlpExporter(properties: TelemetryOtlpProperties): OtlpExporter =
            OtlpExporter(
                endpoint = properties.endpoint,
                headers = properties.headers,
                timeout = properties.timeout.toKotlinDuration(),
                attempts = properties.attempts,
                backoff = properties.backoff.toKotlinDuration(),
                gzip = properties.gzip,
            )
    }

    private companion object {
        /** What a process with no name at all reports, so the failure is visible rather than silent. */
        const val DEFAULT_SERVICE = "unknown-service"
    }
}
