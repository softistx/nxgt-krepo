package com.strange.jpa.scan

import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table

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
