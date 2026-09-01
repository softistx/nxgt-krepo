package com.strange.telemetry.ktor

import com.strange.telemetry.Attributes
import com.strange.telemetry.Telemetry
import com.strange.telemetry.export.Exporter
import com.strange.telemetry.model.Severity
import com.strange.telemetry.trace.Sampler
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How [Observability] builds its telemetry, and what it does with a request. */
class ObservabilityConfiguration {
    /**
     * A telemetry built elsewhere — by a DI container, or by hand and shared with a worker.
     *
     * When there is one, **this plugin will not close it**: the rule the rest of this repository
     * follows is that whoever created it closes it. Without one the plugin builds its own from the
     * settings below and closes it when the application stops.
     */
    var instance: Telemetry? = null

    /** `service.name`. Required unless [instance] is given — there is no sensible default for it. */
    var service: String? = null

    var version: String? = null

    var environment: String? = null

    var attributes: Attributes = Attributes.EMPTY

    var sampler: Sampler = Sampler.always

    var minimum: Severity = Severity.Info

    var stackTraces: Boolean = true

    var batch: Int = 512

    var linger: Duration = 1.seconds

    var drainTimeout: Duration = 10.seconds

    var onExportError: (Throwable) -> Unit = { it.printStackTrace() }

    /**
     * Makes this the process-wide default, so `logger<T>()` in a service class finds it.
     *
     * On by default, and it is what makes the plugin worth installing: a class three layers below a
     * route has no `ApplicationCall` and should not be given one just to write a log.
     */
    var install: Boolean = true

    /**
     * Registers the telemetry with Ktor's DI as well.
     *
     * Off by default, and it has to be: `ktor-server-di` is compile-only here, so an application that
     * never asks for this must not be made to carry it at runtime.
     */
    var injectable: Boolean = false

    /**
     * What a request's span is called before routing has matched it.
     *
     * The default is the method and the raw path, and the plugin **renames it** to the matched route
     * once routing has run — `GET /orders/8d1f…` becomes `GET /orders/{id}`, which is the name a
     * backend can group by. A request that matches nothing keeps the raw path, which is what you want
     * to see when hunting a 404.
     */
    var spanName: (ApplicationCall) -> String = { "${it.request.httpMethod.value} ${it.request.path()}" }

    /**
     * Which requests get a span at all.
     *
     * The default is all of them. A health check answered every second by a load balancer is the
     * usual reason to narrow it: it is a trace nobody will ever read, and it is most of the traces.
     */
    var traced: (ApplicationCall) -> Boolean = { true }

    internal val exporters = mutableListOf<Exporter>()

    /** Adds a destination. Ignored when [instance] is given — that telemetry has its own. */
    fun export(exporter: Exporter) {
        exporters += exporter
    }
}
