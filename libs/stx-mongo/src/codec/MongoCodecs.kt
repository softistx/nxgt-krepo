package com.strange.mongo.codec

import com.mongodb.MongoClientSettings
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlinx.BsonConfiguration
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import kotlin.time.Instant

/**
 * The contextual serializers a Mongo-bound `@Serializable` class can rely on: mark a field
 * `@Contextual` and it is stored the way Mongo wants it. Right now that is [Instant] as a BSON
 * date; per-field `@Serializable(with = InstantAsBsonDateTime::class)` does the same without the
 * module.
 */
val mongoSerializersModule: SerializersModule = SerializersModule { contextual(InstantAsBsonDateTime) }

/**
 * The registry a client should be built with.
 *
 * Order is what makes it work — the first provider that answers wins, so [codecs] override the
 * built-in ones, which override the kotlinx provider, which overrides the driver's defaults.
 * Without the kotlinx provider a `@Serializable` data class only maps if bson-kotlin's reflective
 * codec happens to be on the classpath; with it, mapping follows the annotations the class already
 * carries.
 *
 * ```kotlin
 * MongoClient.create(uri).withCodecRegistry(mongoCodecRegistry())
 * ```
 */
fun mongoCodecRegistry(
    vararg codecs: Codec<*>,
    serializersModule: SerializersModule = mongoSerializersModule,
    bsonConfiguration: BsonConfiguration = BsonConfiguration(),
): CodecRegistry =
    CodecRegistries.fromRegistries(
        CodecRegistries.fromCodecs(*codecs),
        CodecRegistries.fromCodecs(InstantCodec()),
        CodecRegistries.fromProviders(KotlinSerializerCodecProvider(serializersModule, bsonConfiguration)),
        MongoClientSettings.getDefaultCodecRegistry(),
    )
