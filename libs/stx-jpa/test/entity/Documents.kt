package com.softistx.jpa.entity

import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/*
 * What the JSON specs store in one column, and the two ways of getting it there.
 */

/** A document with a defaulted property and a nullable one, which the `jpaJson` scenarios turn on. */
@Serializable
data class Address(
    val street: String = "",
    val city: String = "",
    val country: String = "DE",
    val note: String? = null,
)

/** Mutable on purpose: the scenario about dirty checking needs something it can change in place. */
@Serializable
class Label(
    var text: String = "",
)

/** Deliberately not `@Serializable`, so one scenario can be about the failure. */
class Plain(
    var label: String = "",
)

@Entity
@Table(name = "customers")
class Customer(
    @Id var id: Long = 0,
    @JdbcTypeCode(SqlTypes.JSON) var address: Address = Address(),
    @JdbcTypeCode(SqlTypes.JSON) var tags: Map<String, String> = emptyMap(),
    @JdbcTypeCode(SqlTypes.JSON) var label: Label = Label(),
)

@Entity
@Table(name = "boxes")
class Box(
    @Id var id: Long = 0,
    @JdbcTypeCode(SqlTypes.JSON) var thing: Plain = Plain(),
)

/** A list under the object code, which `rejectMismatchedJsonShapes` refuses before it can be written. */
@Entity
@Table(name = "baskets")
class Basket(
    @Id var id: Long = 0,
    @JdbcTypeCode(SqlTypes.JSON) var labels: List<String> = emptyList(),
)

/** And the mirror of it: an object under the array code. */
@Entity
@Table(name = "bundles")
class Bundle(
    @Id var id: Long = 0,
    @JdbcTypeCode(SqlTypes.JSON_ARRAY) var address: Address = Address(),
)

/** A list under the array code, which is the way to store one. */
@Entity
@Table(name = "crates")
class Crate(
    @Id var id: Long = 0,
    @JdbcTypeCode(SqlTypes.JSON_ARRAY) var labels: List<String> = emptyList(),
)

/**
 * The other route: an `@Embeddable` needs no `@Serializable` and no format mapper at all, because
 * Hibernate knows the shape and writes the document from its own mapping model.
 */
@Embeddable
class Coordinates(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
)

@Entity
@Table(name = "places")
class Place(
    @Id var id: Long = 0,
    @Embedded @JdbcTypeCode(SqlTypes.JSON) var at: Coordinates = Coordinates(),
)
