package com.softistx.spring.data.mongo.convert

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
 * [stxMongoConverters] is them, ready to register.
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
 * The converters this module contributes, as a list to register rather than a bean to install.
 *
 * A list, because Spring Boot's own `MongoCustomConversions` bean is `@ConditionalOnMissingBean`: a
 * library contributing one *replaces* Boot's instead of adding to it, and an application setting
 * `spring.data.mongodb.representation` would silently lose it. `MongoAutoConfiguration` in
 * `data/mongo/config/` registers these ahead of Boot and carries that property across; anything
 * wiring Mongo by hand passes the list itself.
 */
fun stxMongoConverters(): List<Any> = listOf(InstantToDateConverter(), DateToInstantConverter())

/**
 * [stxMongoConverters] as a standalone `MongoCustomConversions`, for a caller building a converter
 * outside Spring Boot — a test harness, a script.
 *
 * **BSON dates hold milliseconds.** A round trip loses anything finer, so an `Instant` read back is
 * not always the one written — which matters for a cursor or an equality check built from a
 * timestamp, and not at all for a `createdAt` somebody displays. Storing a string would keep the
 * nanoseconds and lose range queries and index ordering, which is the worse trade.
 */
fun stxMongoConversions(): MongoCustomConversions = MongoCustomConversions(stxMongoConverters())
