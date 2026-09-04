package com.softistx.jpa.entity

import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table

/*
 * The two inheritance strategies worth proving, one hierarchy each.
 *
 * They are here because nothing exercised inheritance at all and no document mentioned it, which
 * left "does it work" a matter of opinion. The Kotlin part is not free either: an `@Entity` subclass
 * needs its superclass open and both need a no-arg constructor, and the two compiler plugins the
 * module configures — `allOpen` on `@Entity`, `noArg` with the `jpa` preset — are what supply both.
 */

@Entity
@Table(name = "vehicles")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind")
abstract class Vehicle(
    @Id var id: Long = 0,
    var label: String = "",
)

@Entity
@DiscriminatorValue("car")
class Car(
    id: Long = 0,
    label: String = "",
    var doors: Int = 0,
) : Vehicle(id, label)

@Entity
@DiscriminatorValue("truck")
class Truck(
    id: Long = 0,
    label: String = "",
    var tonnes: Int = 0,
) : Vehicle(id, label)

@Entity
@Table(name = "tools")
@Inheritance(strategy = InheritanceType.JOINED)
abstract class Tool(
    @Id var id: Long = 0,
    var name: String = "",
)

@Entity
@Table(name = "hammers")
class Hammer(
    id: Long = 0,
    name: String = "",
    var weightGrams: Int = 0,
) : Tool(id, name)
