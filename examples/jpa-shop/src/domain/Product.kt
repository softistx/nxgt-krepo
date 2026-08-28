package com.strange.example.shop.domain

import com.strange.jpa.audit.AuditedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * An ordinary annotated Kotlin class, which is all an entity ever is here.
 *
 * It extends [AuditedEntity], so `created_at`, `last_modified_at`, `created_by` and
 * `last_modified_by` come with it — the timestamps stamped by Hibernate inside the flush, the two
 * names by whichever service is acting.
 */
@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false, unique = true)
    var sku: String = "",
    @Column(nullable = false)
    var name: String = "",
    var priceInCents: Long = 0,
    var discontinued: Boolean = false,
) : AuditedEntity()
