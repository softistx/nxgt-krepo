package com.softistx.testing.docs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** The helper every Spring module's `ConfigurationDocsTest` stands on, against a fixture page and fixture metadata. */
class SpringConfigurationDocsTest :
    StringSpec({
        "documented keys are narrowed to the groups the metadata declares" {
            val docs =
                springConfigurationDocs(
                    SpringConfigurationDocsTest::class.java,
                    "libs/core/stx-testing/testResources/docs/spring-configuration-fixture.md",
                )

            docs.groups shouldBe setOf("stx.alpha")
            docs.declared shouldBe setOf("stx.alpha.one", "stx.alpha.two")
            docs.documented shouldBe setOf("stx.alpha.one", "stx.alpha.two")
        }

        "a page that does not exist documents nothing" {
            springConfigurationDocs(SpringConfigurationDocsTest::class.java, "docs/no-such-page.md")
                .documented shouldBe emptySet()
        }

        "metadata missing from the classpath fails naming the resource" {
            // A JDK class is loaded by the boot loader, which cannot see the test resources.
            shouldThrow<IllegalStateException> { springConfigurationDocs(String::class.java) }
                .message shouldContain "is not on the classpath"
        }
    })
