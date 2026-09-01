package com.softistx.telemetry.mongo

import com.softistx.telemetry.export.signalJson
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Signal
import com.softistx.telemetry.model.SpanRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.bson.Document
import java.util.Date

/**
 * A signal as the document Mongo stores.
 *
 * It goes through [signalJson] first, so **the field names are the ones the file and the stdout
 * exporters write** — a team that moves from one to the other keeps its queries, and a document here
 * and a line there are the same record rather than two shapes of it.
 *
 * ## Except the instants, which are BSON dates
 *
 * JSON has no date, so `signalJson` renders an [kotlin.time.Instant] as an ISO-8601 string, and a
 * string is not something Mongo will expire, compare or index usefully. The three time fields are
 * therefore written back from the typed signal after the conversion — not fished out of the JSON by
 * name, which would go quietly wrong the day a field is renamed.
 *
 * ## And the resource, which the line format does not carry
 *
 * A file belongs to one service; a collection does not. `service`, and `version` and `environment`
 * when they are set, are written onto every document because the alternative is a collection nobody
 * can filter — and the resource is the attribute every backend groups by. It is the one deliberate
 * difference from what [signalJson] alone produces.
 */
internal fun Signal.document(resource: Resource): Document {
    val document = signalJson.encodeToJsonElement(Signal.serializer(), this).jsonObject.toDocument()
    document["at"] = Date(at.toEpochMilliseconds())
    document["service"] = resource.service
    resource.version?.let { document["version"] = it }
    resource.environment?.let { document["environment"] = it }
    if (this is SpanRecord) {
        document["startedAt"] = Date(startedAt.toEpochMilliseconds())
        document["endedAt"] = Date(endedAt.toEpochMilliseconds())
    }
    return document
}

private fun JsonObject.toDocument(): Document = Document(mapValues { (_, value) -> value.toBson() })

/**
 * The BSON a JSON value becomes.
 *
 * A number is a `Long` when it is whole and a `Double` when it is not, which matters because an
 * attribute's numbers arrive here having lost their Kotlin type on the way through JSON. Storing
 * every one of them as a double would make `http.response.status_code` come back as `200.0` and turn
 * an exact-match query into a floating-point comparison.
 */
private fun JsonElement.toBson(): Any? =
    when (this) {
        is JsonNull -> {
            null
        }

        is JsonObject -> {
            toDocument()
        }

        is JsonArray -> {
            map { it.toBson() }
        }

        is JsonPrimitive -> {
            when {
                isString -> content
                else -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
            }
        }
    }
