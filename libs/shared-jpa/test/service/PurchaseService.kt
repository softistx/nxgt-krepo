package com.strange.jpa.service

import com.strange.jpa.entity.Purchase
import com.strange.jpa.repository.JpaRepository
import com.strange.jpa.session.JpaSession

internal data class NewPurchase(
    val id: Long,
    val reference: String,
    val total: Long = 0,
)

internal data class EditPurchase(
    val reference: String? = null,
    val total: Long? = null,
)

/**
 * The service from the KDoc, with every hook recording that it ran — which is how the specs assert
 * on ordering and on what a failing hook takes down with it.
 */
internal open class PurchaseService(
    principal: String? = null,
) : JpaCrudService<Purchase, Long, NewPurchase, EditPurchase>(
        JpaRepository(Purchase::id),
        principal,
    ) {
    val ran = mutableListOf<String>()

    override suspend fun buildCreate(input: NewPurchase) = Purchase(input.id, input.reference, input.total)

    override suspend fun applyUpdate(
        existing: Purchase,
        input: EditPurchase,
    ) {
        input.reference?.let { existing.reference = it }
        input.total?.let { existing.total = it }
    }

    /** Whoever is acting goes into the reference, which is the one string this fixture entity has. */
    override fun stampUpdated(entity: Purchase) {
        principal?.let { entity.reference = "${entity.reference}/$it" }
    }

    override suspend fun beforeCreate(input: NewPurchase) {
        ran += "beforeCreate"
    }

    override suspend fun afterCreate(
        created: Purchase,
        session: JpaSession,
    ) {
        ran += "afterCreate"
    }

    override suspend fun beforeUpdate(
        existing: Purchase,
        input: EditPurchase,
    ) {
        ran += "beforeUpdate"
    }

    override suspend fun afterUpdate(
        updated: Purchase,
        session: JpaSession,
    ) {
        ran += "afterUpdate"
    }

    override suspend fun beforeDelete(
        ids: Collection<Long>,
        session: JpaSession,
    ) {
        ran += "beforeDelete"
    }

    override suspend fun afterDelete(
        ids: Collection<Long>,
        session: JpaSession,
    ) {
        ran += "afterDelete"
    }
}
