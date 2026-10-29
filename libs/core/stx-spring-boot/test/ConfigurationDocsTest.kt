package com.softistx.spring

import com.softistx.testing.docs.springConfigurationDocs
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * `docs/spring-configuration.md` documents every `stx.*` key, and nothing it no longer has.
 *
 * The second hand-written file over the same data, and it rots the same way: `ConfigurationMetadataTest`
 * keeps the IDE's metadata honest, and this keeps the page a person reads honest. Both failure modes are
 * silent — a key with no entry is autocompletion quietly missing one and a reader quietly not finding it,
 * and neither gets reported as a bug.
 *
 * Worth stating why this is a spec rather than a review habit: while writing that page I checked it by
 * hand three times and got two different wrong answers, because the checking script — not the page — was
 * wrong. A check nobody runs the same way twice is not a check.
 */
class ConfigurationDocsTest :
    StringSpec({
        val docs = springConfigurationDocs(ConfigurationDocsTest::class.java)
        val documented = docs.documented

        "the reference page was found" {
            // Guards the specs below: a page this cannot locate would pass them both vacuously.
            documented.isNotEmpty() shouldBe true
        }

        "every stx.* key is on the page" {
            withClue("add the key to docs/spring-configuration.md in the change that reads it") {
                (docs.declared - documented).sorted() shouldBe emptyList()
            }
        }

        "the page documents nothing the code no longer declares" {
            (documented - docs.declared).sorted() shouldBe emptyList()
        }
    })
