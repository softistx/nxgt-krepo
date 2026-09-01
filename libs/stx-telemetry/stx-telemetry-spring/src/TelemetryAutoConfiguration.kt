package com.strange.telemetry.spring

import com.strange.telemetry.Telemetry
import com.strange.telemetry.export.ConsoleExporter
import com.strange.telemetry.export.Exporter
import com.strange.telemetry.export.FileExporter
import com.strange.telemetry.export.JsonLinesExporter
import com.strange.telemetry.otlp.OtlpExporter
import com.strange.telemetry.slf4j.Slf4jExporter
import com.strange.telemetry.trace.Sampler
import org.slf4j.ILoggerFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.nio.file.Path
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
 * with a destination of its own declares a bean and says nothing else; `stx.telemetry.otlp` and
 * `stx.telemetry.slf4j` are there because the two common cases should not need a `@Bean` method.
 *
 * The root is [Telemetry.install]ed, which is what makes `logger<T>()` and `span { }` work in a
 * service class with nothing injected — and it is closed by the context, which drains the queue.
 */
@AutoConfiguration
@EnableConfigurationProperties(
    TelemetryProperties::class,
    TelemetryOtlpProperties::class,
    TelemetrySlf4jProperties::class,
    TelemetryFileProperties::class,
)
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

    /**
     * The bridge pointed outward: this application's signals, written to its own SLF4J.
     *
     * The counterpart to `stx-telemetry-slf4j`'s `SLF4JServiceProvider`, which needs no key because a
     * provider is bound by being on the classpath. **The two directions together are a loop**, and
     * `Slf4jExporter`'s own constructor refuses it — so an application that turns this on with the
     * provider also bound fails to start, with a message naming both ways out, rather than filling a
     * queue with its own output at some later hour.
     *
     * An `ILoggerFactory` bean, if there is one, is where the lines go. That is how an application
     * with two SLF4J contexts — a plugin host, an embedded server — says which one; without a bean
     * it is the bound factory, which is the whole point of this exporter.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Slf4jExporter::class)
    @ConditionalOnProperty(prefix = "stx.telemetry.slf4j", name = ["enabled"], havingValue = "true")
    class Slf4j {
        @Bean
        @ConditionalOnMissingBean(Slf4jExporter::class)
        fun stxSlf4jExporter(
            properties: TelemetrySlf4jProperties,
            factory: ObjectProvider<ILoggerFactory>,
        ): Slf4jExporter =
            Slf4jExporter(
                spans = properties.spans,
                spanSeverity = properties.spanSeverity,
                factory = factory.getIfAvailable { LoggerFactory.getILoggerFactory() },
            )
    }

    /**
     * A rotating file on the local disk.
     *
     * The only one of the three nested configurations with no `@ConditionalOnClass`: `FileExporter`
     * is in `stx-telemetry` itself, which this module depends on and re-exports, so an application
     * that reached this class already has it. Guarding it on a class that cannot be absent would read
     * as a warning about a risk that does not exist.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stx.telemetry.file", name = ["enabled"], havingValue = "true")
    class File {
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(FileExporter::class)
        fun stxFileExporter(properties: TelemetryFileProperties): FileExporter =
            FileExporter(
                path = Path.of(properties.path),
                maxSize = properties.maxSize.toBytes(),
                // Zero is how a duration says "never" here. `every: 0` in a yaml file is a limit
                // turned off, where an empty value would be a limit somebody forgot to fill in.
                every = properties.every.takeUnless { it.isZero }?.toKotlinDuration(),
                keep = properties.keep,
                compress = properties.compress,
            )
    }

    private companion object {
        /** What a process with no name at all reports, so the failure is visible rather than silent. */
        const val DEFAULT_SERVICE = "unknown-service"
    }
}
