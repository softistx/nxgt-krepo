package com.strange.spring.data.mongo.convert

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import java.util.Date
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * What lets a `kotlin.time.Instant` be a field on a document.
 *
 * BSON has one date type and the driver has a codec for `java.util.Date`; it has none for
 * `kotlin.time.Instant`, and the failure is `Can't find a codec for kotlin.time.Instant` — raised
 * when the query runs, not when the entity is mapped. These two converters are the whole fix, and
 * [stxMongoConversions] is them registered.
 *
 * The same gap is why `stx-mongo` has `mongoCodecRegistry()`: two layers over the same driver, each
 * needing to be told about the same type.
 */
@WritingConverter
class InstantToDateConverter : Converter<Instant, Date> {
    override fun convert(source: Instant): Date = Date.from(source.toJavaInstant())
}

/** The other direction. See [InstantToDateConverter]. */
@ReadingConverter
class DateToInstantConverter : Converter<Date, Instant> {
    override fun convert(source: Date): Instant = source.toInstant().toKotlinInstant()
}

/**
 * The conversions this module contributes, as a bean an application can add to.
 *
 * **BSON dates hold milliseconds.** A round trip through this loses anything finer, so an `Instant`
 * read back is not always the one written — which matters for a cursor or an equality check built
 * from a timestamp, and matters not at all for a `createdAt` somebody displays. Storing a string
 * would keep the nanoseconds and lose range queries and index ordering, which is the worse trade.
 */
fun stxMongoConversions(): MongoCustomConversions = MongoCustomConversions(listOf(InstantToDateConverter(), DateToInstantConverter()))
