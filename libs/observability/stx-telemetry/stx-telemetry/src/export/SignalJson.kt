package com.softistx.telemetry.export

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration every exporter that writes JSON uses.
 *
 * [JsonLinesExporter] writes it a line at a time, [FileExporter] writes the same lines to a file that
 * rotates, and `stx-telemetry-mongo` encodes through it before turning the object into BSON. Public
 * because an application writing an exporter of its own wants the same three answers, and because a
 * copy in each of them would be one edit away from making a consumer able to tell which exporter
 * wrote a record — which is exactly what none of them should be able to do.
 *
 * - **Defaults are written out**, unlike everywhere else in this repository. A collector's schema is
 *   happier with a field that is always present, and "severity absent means Info" is a rule every
 *   consumer would otherwise have to be told about separately.
 * - **The discriminator is `type`**, and its values are `log` and `span` rather than Kotlin class
 *   names: those are `@SerialName`s on the records, so a parser is not coupled to this library's
 *   packages.
 * - **Nulls are dropped.** An absent `error` is one fewer field on every record that did not fail,
 *   which is nearly all of them.
 */
val signalJson =
    Json {
        encodeDefaults = true
        classDiscriminator = "type"
        explicitNulls = false
    }
