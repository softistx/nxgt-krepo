package com.softistx.graphix.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The two directions between kotlinx.serialization's `JsonElement` and the plain Java values
 * graphql-java speaks — `Map`, `List`, `Number`, `Boolean`, `String`, `null`.
 *
 * graphql-java has no idea what a `JsonElement` is: variables arrive as those Java values, a
 * resolver's result leaves as them, and this module's JSON is kotlinx everywhere else. Three
 * packages needed the conversion, so it lives in one file rather than three private copies that
 * drift.
 */
internal fun JsonElement.toJava(): Any? =
    when (this) {
        is JsonNull -> {
            null
        }

        is JsonPrimitive -> {
            if (isString) {
                content
            } else {
                booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
            }
        }

        is JsonObject -> {
            mapValues { it.value.toJava() }
        }

        is JsonArray -> {
            map { it.toJava() }
        }
    }

/** The other direction. A value of no JSON shape is its `toString` — never a silent drop. */
internal fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
        is List<*> -> JsonArray(map { it.toJsonElement() })
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }
