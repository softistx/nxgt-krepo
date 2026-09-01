package com.softistx.openapi.models

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
internal val DESERIALIZATION_STRATEGY = ClassName("kotlinx.serialization", "DeserializationStrategy")
internal val SERIALIZATION_EXCEPTION = ClassName("kotlinx.serialization", "SerializationException")
internal val EXPERIMENTAL_SERIALIZATION_API =
    ClassName("kotlinx.serialization", "ExperimentalSerializationApi")
internal val ENCODE_DEFAULT = ClassName("kotlinx.serialization", "EncodeDefault")
internal val JSON_ELEMENT = ClassName("kotlinx.serialization.json", "JsonElement")
internal val JSON_IGNORE_UNKNOWN_KEYS = ClassName("kotlinx.serialization.json", "JsonIgnoreUnknownKeys")
internal val JSON_CONTENT_POLYMORPHIC_SERIALIZER =
    ClassName("kotlinx.serialization.json", "JsonContentPolymorphicSerializer")
internal val JSON_OBJECT_MEMBER = MemberName("kotlinx.serialization.json", "jsonObject")
internal val JSON_PRIMITIVE_MEMBER = MemberName("kotlinx.serialization.json", "jsonPrimitive")
internal val CONTENT_OR_NULL = MemberName("kotlinx.serialization.json", "contentOrNull")

// Jackson 3 moved its databind packages, but the annotations stayed at com.fasterxml.
internal val JSON_PROPERTY = ClassName("com.fasterxml.jackson.annotation", "JsonProperty")
internal val JSON_VALUE = ClassName("com.fasterxml.jackson.annotation", "JsonValue")
internal val JSON_CREATOR = ClassName("com.fasterxml.jackson.annotation", "JsonCreator")
internal val JSON_TYPE_INFO = ClassName("com.fasterxml.jackson.annotation", "JsonTypeInfo")
internal val JSON_SUB_TYPES = ClassName("com.fasterxml.jackson.annotation", "JsonSubTypes")
internal val JSON_IGNORE_PROPERTIES = ClassName("com.fasterxml.jackson.annotation", "JsonIgnoreProperties")
