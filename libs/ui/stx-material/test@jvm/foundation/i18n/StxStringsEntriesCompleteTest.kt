package com.softistx.material.foundation.i18n

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

/** `entries()` is hand-written; reflection over the class's own fields is what keeps it complete. */
class StxStringsEntriesCompleteTest :
    FeatureSpec({
        feature("completeness") {
            scenario("entries() covers every key of StxStrings") {
                val keys =
                    StxStrings.En
                        .entries()
                        .keys
                        .map { it.substringBefore('(') }
                        .toSet()
                val properties =
                    StxStrings::class.java.declaredFields
                        .filterNot { Modifier.isStatic(it.modifiers) }
                        .map { it.name }
                        .toSet()
                keys shouldBe properties
            }
        }
    })
