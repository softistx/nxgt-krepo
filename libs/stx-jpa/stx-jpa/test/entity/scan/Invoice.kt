package com.softistx.jpa.entity.scan

import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table

/*
 * A package of its own, under `entity` with the rest, because `EntityScanTest` is about a package
 * boundary: it asserts what a scan of one package finds, so the package has to hold these three
 * classes and nothing else. Put them beside the other fixtures and the spec asserts over whatever
 * entity anybody adds next — including `UuidId`, which this library refuses on purpose.
 */

/** A mapped superclass, which is not an entity and still has to be mapped. */
@MappedSuperclass
abstract class Auditable(
    var createdBy: String = "",
)

/** An embeddable, likewise. */
@Embeddable
class Money(
    var amount: Long = 0,
    var currency: String = "",
)

/** An entity that is only mappable if both of the above are too. */
@Entity
@Table(name = "invoices")
class Invoice(
    @Id var id: Long = 0,
    @Embedded var total: Money = Money(),
) : Auditable()
