package com.strange.spring.data.mongo.criteria

import org.springframework.data.geo.Circle
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import java.util.regex.Pattern
import kotlin.reflect.KProperty

// A Mongo predicate as an infix expression: `Product::stock gt 0` rather than
// `Criteria.where("stock").gt(0)`.
//
// Every operator comes in two forms, and the `KProperty` one is the point. A field named by a string
// is a field name nothing checks: rename the property and the query keeps compiling and silently
// matches nothing — which reads as "no results" and not as "broken query". `Product::stock` is the
// same query with the compiler holding the name. The string forms remain for the fields that have no
// property to name them: a value inside a `Map`, or a path built from a request.

/** `field = value`. */
infix fun String.eq(value: Any?): Criteria = where(this).`is`(value)

/** `field = value`, with the compiler holding the field name. */
infix fun KProperty<*>.eq(value: Any?): Criteria = name eq value

/** `field != value`. */
infix fun String.ne(value: Any?): Criteria = where(this).ne(value)

/** `field != value`, with the compiler holding the field name. */
infix fun KProperty<*>.ne(value: Any?): Criteria = name ne value

/** `field < value`. */
infix fun String.lt(value: Any): Criteria = where(this).lt(value)

/** `field < value`, with the compiler holding the field name. */
infix fun KProperty<*>.lt(value: Any): Criteria = name lt value

/** `field <= value`. */
infix fun String.lte(value: Any): Criteria = where(this).lte(value)

/** `field <= value`, with the compiler holding the field name. */
infix fun KProperty<*>.lte(value: Any): Criteria = name lte value

/** `field > value`. */
infix fun String.gt(value: Any): Criteria = where(this).gt(value)

/** `field > value`, with the compiler holding the field name. */
infix fun KProperty<*>.gt(value: Any): Criteria = name gt value

/** `field >= value`. */
infix fun String.gte(value: Any): Criteria = where(this).gte(value)

/** `field >= value`, with the compiler holding the field name. */
infix fun KProperty<*>.gte(value: Any): Criteria = name gte value

/** `field` is one of [values]. Duplicates are dropped, since Mongo does not care and the index does not either. */
infix fun String.oneOf(values: Collection<Any?>): Criteria = where(this).`in`(values.toSet())

/** `field` is one of [values], with the compiler holding the field name. */
infix fun KProperty<*>.oneOf(values: Collection<Any?>): Criteria = name oneOf values

/** `field` is none of [values]. */
infix fun String.noneOf(values: Collection<Any?>): Criteria = where(this).nin(values.toSet())

/** `field` is none of [values], with the compiler holding the field name. */
infix fun KProperty<*>.noneOf(values: Collection<Any?>): Criteria = name noneOf values

/**
 * Whether the field is present in the document at all.
 *
 * Not the same question as `eq null`, and the difference bites: a document written before a field
 * existed has no key, while one written after with nothing to put there has the key set to null.
 * `exists false` finds the first and not the second.
 */
infix fun String.exists(present: Boolean): Criteria = where(this).exists(present)

/** [exists], with the compiler holding the field name. */
infix fun KProperty<*>.exists(present: Boolean): Criteria = name exists present

/** An array field with exactly this many elements. */
infix fun String.size(count: Int): Criteria = where(this).size(count)

/** [size], with the compiler holding the field name. */
infix fun KProperty<*>.size(count: Int): Criteria = name size count

/**
 * A regular expression against the field.
 *
 * Takes a compiled [Pattern] rather than a string so that flags — case insensitivity, most often —
 * are set where they can be seen, instead of smuggled in as `(?i)` at the front of a pattern.
 * [containing] and [containingIgnoringCase] are the two everyday cases spelled out.
 */
infix fun String.matching(pattern: Pattern): Criteria = where(this).regex(pattern)

/** [matching], with the compiler holding the field name. */
infix fun KProperty<*>.matching(pattern: Pattern): Criteria = name matching pattern

/**
 * A substring search.
 *
 * [text] is quoted before it becomes a pattern. Without that, a search box is a way to hand the
 * database a regular expression — `(a+)+$` against a long field is a request that does not come back,
 * and it does not need a hostile user to arrive, only a search for `C++`.
 */
infix fun String.containing(text: String): Criteria = this matching substringPattern(text, ignoringCase = false)

/** [containing], with the compiler holding the field name. */
infix fun KProperty<*>.containing(text: String): Criteria = name containing text

/** [containing], ignoring case. */
infix fun String.containingIgnoringCase(text: String): Criteria = this matching substringPattern(text, ignoringCase = true)

/** [containingIgnoringCase], with the compiler holding the field name. */
infix fun KProperty<*>.containingIgnoringCase(text: String): Criteria = name containingIgnoringCase text

/**
 * The negation of [matching].
 *
 * Built as `where(field).not().regex(...)` and not as `(field matching pattern).not()`, which is the
 * shape this was ported in and does nothing at all: Spring's `Criteria.not()` sets a flag that the
 * *next* operator consumes, so calling it after `regex` negates nothing and the query silently keeps
 * matching what it was meant to exclude. The spec for `!like` caught it.
 */
infix fun String.notMatching(pattern: Pattern): Criteria = where(this).not().regex(pattern)

/** [notMatching], with the compiler holding the field name. */
infix fun KProperty<*>.notMatching(pattern: Pattern): Criteria = name notMatching pattern

/** The negation of [containing], quoted the same way. */
infix fun String.notContaining(text: String): Criteria = this notMatching substringPattern(text, ignoringCase = false)

/** [notContaining], with the compiler holding the field name. */
infix fun KProperty<*>.notContaining(text: String): Criteria = name notContaining text

/** The negation of [containingIgnoringCase]. */
infix fun String.notContainingIgnoringCase(text: String): Criteria = this notMatching substringPattern(text, ignoringCase = true)

/** [notContainingIgnoringCase], with the compiler holding the field name. */
infix fun KProperty<*>.notContainingIgnoringCase(text: String): Criteria = name notContainingIgnoringCase text

/** Documents whose geo field is near [point], nearest first. Needs a geospatial index. */
infix fun String.near(point: Point): Criteria = where(this).near(point)

/** [near], with the compiler holding the field name. */
infix fun KProperty<*>.near(point: Point): Criteria = name near point

/** Documents whose geo field falls inside [circle]. */
infix fun String.within(circle: Circle): Criteria = where(this).within(circle)

/** [within], with the compiler holding the field name. */
infix fun KProperty<*>.within(circle: Circle): Criteria = name within circle

/** The quoted substring pattern the four `containing` operators share. */
private fun substringPattern(
    text: String,
    ignoringCase: Boolean,
): Pattern = Pattern.compile(".*${Pattern.quote(text)}.*", if (ignoringCase) Pattern.CASE_INSENSITIVE else 0)
