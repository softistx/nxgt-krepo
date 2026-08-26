package com.strange.openapi.models

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

/**
 * Every type the generated code names from a serialization library, in one place.
 *
 * They are declared rather than imported: this module compiles against neither library, and must
 * not, or a consumer would be forced to put both on their classpath to use either.
 */

internal val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
internal val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")
internal val K_SERIALIZER = ClassName("kotlinx.serialization", "KSerializer")
internal val SERIAL_DESCRIPTOR = ClassName("kotlinx.serialization.descriptors", "SerialDescriptor")
internal val PRIMITIVE_KIND = ClassName("kotlinx.serialization.descriptors", "PrimitiveKind")
internal val PRIMITIVE_DESCRIPTOR =
    MemberName("kotlinx.serialization.descriptors", "PrimitiveSerialDescriptor")
internal val ENCODER = ClassName("kotlinx.serialization.encoding", "Encoder")
internal val DECODER = ClassName("kotlinx.serialization.encoding", "Decoder")

// Jackson 3 moved its databind packages, but the annotations stayed at com.fasterxml.
internal val JSON_PROPERTY = ClassName("com.fasterxml.jackson.annotation", "JsonProperty")
internal val JSON_VALUE = ClassName("com.fasterxml.jackson.annotation", "JsonValue")
internal val JSON_CREATOR = ClassName("com.fasterxml.jackson.annotation", "JsonCreator")
