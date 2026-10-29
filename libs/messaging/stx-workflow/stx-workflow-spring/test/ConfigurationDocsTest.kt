package com.softistx.workflow.spring

import com.softistx.testing.docs.springConfigurationDocs
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * `docs/spring-configuration.md` documents every `stx.workflow` key, and nothing this module no
 * longer has.
 *
 * The page is one page for every `stx.*` key an application can set, but its sections belong to
 * different modules — so **each module checks its own**. `stx-spring-boot` and `stx-graphix-spring`
 * have the twins of this spec, scoped the same way. This half arrived with the module: the moment
 * `stx.workflow` left the hub, nobody was checking that section any more.
 *
 * The failure mode is silent — a key with no entry is autocompletion quietly missing one and a
 * reader quietly not finding it.
 */
class ConfigurationDocsTest :
    StringSpec({
        val docs = springConfigurationDocs(ConfigurationDocsTest::class.java)
        val documented = docs.documented

        "the reference page was found" {
            // Guards the specs below: a page this cannot locate would pass them both vacuously.
            documented.isNotEmpty() shouldBe true
        }

        "every stx.workflow key is on the page" {
            withClue("add the key to docs/spring-configuration.md in the change that reads it") {
                (docs.declared - documented).sorted() shouldBe emptyList()
            }
        }

        "the page documents nothing this module no longer declares" {
            (documented - docs.declared).sorted() shouldBe emptyList()
        }
    })
