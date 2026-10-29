package com.softistx.graphix.spring

import com.softistx.testing.docs.springConfigurationDocs
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * `docs/spring-configuration.md` documents every `stx.graphix` key, and nothing this module no
 * longer has.
 *
 * The page is one page for every `stx.*` key an application can set, but its sections belong to
 * different modules — so **each module checks its own**. `stx-spring-boot` has the twin of this
 * spec, scoped the same way; without this half, the `stx.graphix` section was documented by nobody
 * and drifted the moment a key was added, which is exactly what happened.
 *
 * `ConfigurationMetadataTest` beside this one keeps the metadata honest against the code. This keeps
 * the page honest against the metadata. Both failure modes are silent — a key with no entry is
 * autocompletion quietly missing one and a reader quietly not finding it.
 */
class ConfigurationDocsTest :
    StringSpec({
        val docs = springConfigurationDocs(ConfigurationDocsTest::class.java)
        val documented = docs.documented

        "the reference page was found" {
            // Guards the specs below: a page this cannot locate would pass them both vacuously.
            documented.isNotEmpty() shouldBe true
        }

        "every stx.graphix key is on the page" {
            withClue("add the key to docs/spring-configuration.md in the change that reads it") {
                (docs.declared - documented).sorted() shouldBe emptyList()
            }
        }

        "the page documents nothing this module no longer declares" {
            (documented - docs.declared).sorted() shouldBe emptyList()
        }
    })
