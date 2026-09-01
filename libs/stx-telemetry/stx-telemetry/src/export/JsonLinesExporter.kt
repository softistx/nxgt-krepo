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
        for (signal in batch) out.println(signalJson.encodeToString(Signal.serializer(), signal))
        out.flush()
    }
}
