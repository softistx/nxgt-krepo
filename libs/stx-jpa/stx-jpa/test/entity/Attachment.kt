package com.softistx.jpa.entity

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.Any
import org.hibernate.annotations.AnyDiscriminator
import org.hibernate.annotations.AnyDiscriminatorValue
import org.hibernate.annotations.AnyKeyJavaClass

/*
 * An association whose target is not one table.
 *
 * `@Any` is the last mapping on the list `docs/jpa-mapping.md` left unmeasured, and the one with the
 * least prior art in a reactive session: the target's *type* is a column, so resolving the
 * association is a second select that Hibernate cannot plan into the first — there is nothing to
 * join to until the discriminator has been read.
 *
 * That is the same shape as every other trap in this module's table, which makes the prediction
 * obvious and the measurement worth taking anyway.
 */

/**
 * A note attached to whichever entity the discriminator names.
 *
 * `fetch = LAZY` is written out rather than left to the default: `@Any` defaults to `EAGER`, and
 * this module's rule is that a query says what it loads.
 */
@Entity
@Table(name = "attachments")
class Attachment(
    @Id var id: Long = 0,
    var body: String = "",
    @Any(fetch = FetchType.LAZY)
    @AnyDiscriminator(DiscriminatorType.STRING)
    @AnyDiscriminatorValue(discriminator = "buyer", entity = Buyer::class)
    @AnyDiscriminatorValue(discriminator = "thing", entity = Thing::class)
    @AnyKeyJavaClass(Long::class)
    @Column(name = "target_type")
    @JoinColumn(name = "target_id")
    var target: kotlin.Any? = null,
)
