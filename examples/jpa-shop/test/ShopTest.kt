package com.strange.example.shop

import com.strange.example.shop.domain.Product
import com.strange.example.shop.model.EditProduct
import com.strange.example.shop.model.NewProduct
import com.strange.example.shop.model.view
import com.strange.jpa.audit.stampedBy
import com.strange.jpa.audit.touchedBy
import com.strange.jpa.scan.scanEntities
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * What this example can assert without a database.
 *
 * The layer itself is covered by `stx-jpa`'s own specs, which run against a real Postgres in a
 * schema of their own; repeating that here would mean either a second container or writing into the
 * workspace server, and neither belongs in an example. What is worth pinning here is what a
 * *consumer* module sees.
 */
class ShopTest :
    FeatureSpec({

        feature("the mapping") {
            scenario("is found by scanning the domain package, so nothing names the entity class") {
                // `ShopServer` installs the plugin with `packages(…)` and never mentions `Product`.
                // The failure mode of a scan is finding nothing and starting perfectly, so the
                // package name is worth an assertion rather than a comment.
                scanEntities("com.strange.example.shop.domain") shouldContain Product::class
            }
        }

        feature("the audit stamp") {
            // The half stx-jpa's own specs cannot check from the inside: that the stamp applies
            // to an entity declared in another module, and that it chains.
            scenario("names the principal on a create, and leaves the timestamps to Hibernate") {
                val product = Product(sku = "A-1", name = "Anvil").stampedBy("ada")

                product.createdBy shouldBe "ada"
                product.lastModifiedBy shouldBe "ada"
                product.createdAt shouldBe Instant.fromEpochSeconds(0)
            }

            scenario("an unknown principal writes no name, rather than an empty one") {
                val product = Product(sku = "A-1", name = "Anvil").stampedBy("ada").touchedBy(null)

                product.lastModifiedBy shouldBe "ada"
            }
        }

        feature("the payloads") {
            scenario("an edit leaves out what it does not change") {
                val decoded = Json.decodeFromString<EditProduct>("""{"priceInCents":250}""")

                decoded.priceInCents shouldBe 250L
                decoded.name shouldBe null
                decoded.discontinued shouldBe null
            }

            scenario("a create carries no identifier, because a client does not choose one") {
                val decoded = Json.decodeFromString<NewProduct>("""{"sku":"A-1","name":"Anvil","priceInCents":900}""")

                decoded.sku shouldBe "A-1"
                decoded.name shouldBe "Anvil"
            }

            scenario("a view carries the audit trail the entity kept for itself") {
                val product = Product(id = 7, sku = "A-1", name = "Anvil", priceInCents = 900)
                product.createdBy = "ada"

                val view = product.view()

                view.id shouldBe 7L
                view.sku shouldBe "A-1"
                view.createdBy shouldBe "ada"
                view.lastModifiedAt shouldBe product.lastModifiedAt.toString()
            }
        }
    })
