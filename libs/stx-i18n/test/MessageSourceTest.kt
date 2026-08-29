package com.strange.i18n

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.Locale

/**
 * Reading catalogs off the classpath, which is also the first proof that `testResources/` reaches
 * it — no module in this repo used that directory before this one.
 *
 * The scenario that earns its place is the encoding one. The project this module was ported from
 * ships a French catalog encoded as ISO-8859-1, and it works only because the JDK's own loader
 * retries the whole file as Latin-1 when the UTF-8 decode fails. A file that is *mostly* valid
 * UTF-8 gets no such retry, and the accents arrive as `?` in a language the team cannot proofread.
 */
class MessageSourceTest :
    FeatureSpec({

        feature("properties on the classpath") {
            scenario("the unsuffixed catalog is the root one") {
                val root = PropertiesSource().load(Locale.ROOT)

                root!! shouldContainKey "orders.title"
                root["orders.title"] shouldBe "Orders"
            }

            scenario("a language suffix, and a language-and-region one") {
                PropertiesSource().load(Locale.FRENCH)!!["orders.title"] shouldBe "Commandes"
                PropertiesSource().load(Locale.CANADA_FRENCH)!!["checkout.button"] shouldBe "Passer la commande maintenant"
            }

            scenario("a locale nobody shipped is null, not an exception and not somebody else's catalog") {
                PropertiesSource().load(Locale.of("es")) shouldBe null
            }

            scenario("accented text survives, because the file is UTF-8 and is read as UTF-8") {
                PropertiesSource().load(Locale.FRENCH)!!["accented.message"] shouldBe
                    "L'attribut à mettre à jour doit être du même type"
            }
        }

        feature("a catalog that is not UTF-8") {
            scenario("it fails, naming the file, rather than decoding into something almost right") {
                val failure =
                    shouldThrow<CatalogException> {
                        PropertiesSource(baseName = "broken/latin1").load(Locale.ROOT)
                    }

                failure.resource shouldBe "broken/latin1.properties"
                failure.message!! shouldContain "UTF-8"
            }
        }

        feature("catalogs held in memory") {
            scenario("a source needs no files, so a spec needs no fixtures") {
                val source = MapSource(Locale.ENGLISH to mapOf("hello" to "Hello"))

                source.load(Locale.ENGLISH) shouldBe mapOf("hello" to "Hello")
                source.load(Locale.FRENCH) shouldBe null
            }
        }
    })
