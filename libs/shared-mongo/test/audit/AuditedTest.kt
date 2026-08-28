package com.strange.mongo.audit

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.Serializable

@Serializable
private data class Signed(
    val text: String = "",
    override val metadata: AuditMetadata = AuditMetadata(),
) : Audited

/**
 * The stamp an update carries. No server: these are `Bson` operators, and what is worth pinning is
 * when there is nothing to stamp with.
 */
class AuditedTest :
    FeatureSpec({

        feature("stamping an update") {
            scenario("a known principal produces the operators to combine with the update") {
                val stamp = Signed().updatedBy("ada")

                stamp.size shouldBe 1
                stamp.single().toString() shouldNotBe ""
            }

            // Writing an empty string here would overwrite whoever really did touch it last, which
            // is worse than the update saying nothing about who made it.
            scenario("no principal stamps nothing at all, rather than an empty name") {
                Signed().updatedBy(null) shouldBe emptyList()
            }
        }
    })
