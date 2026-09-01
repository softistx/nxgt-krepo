package com.strange.telemetry.export

import com.strange.telemetry.model.Resource
import com.strange.telemetry.model.Signal
import kotlinx.serialization.json.Json
import java.io.PrintStream

/**
 * One JSON object per line, which is what a log collector reads.
 *
 * The signal's own `@Serializable` shape is the document — `{"type":"log","at":…,"severity":…}` —
 * so **this exporter has no format of its own to keep in step with the model.** A field added to
 * [com.strange.telemetry.model.LogRecord] appears here on the next build, without anybody
 * remembering to add it, which is the argument for kotlinx.serialization being the wire format
 * rather than a hand-written renderer.
 *
 * The discriminator is `type`, and it is `log` or `span` rather than a Kotlin class name: those are
 * `@SerialName`s on the records, so a consumer's parser is not coupled to this library's packages.
 */
class JsonLinesExporter(
    private val out: PrintStream = System.out,
) : Exporter {
    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        for (signal in batch) out.println(signalLines.encodeToString(Signal.serializer(), signal))
        out.flush()
    }
}

/**
 * The line format, shared with [FileExporter] rather than written twice.
 *
 * Defaults are written out, unlike everywhere else in this repository: a collector's schema is
 * happier with a field that is always present, and "severity absent means Info" is a rule every
 * consumer would otherwise have to be told about separately.
 *
 * Top-level and `internal` because two exporters produce this format and a consumer reading both
 * must not be able to tell which one wrote a line. A copy in each would be one edit away from
 * telling them apart.
 */
internal val signalLines =
    Json {
        encodeDefaults = true
        classDiscriminator = "type"
        explicitNulls = false
    }
