package com.strange.openapi.plugin

import com.strange.openapi.ktorfit.KtorfitEmitter
import com.strange.openapi.models.ModelStyle
import com.strange.openapi.models.ModelsOnlyEmitter
import com.strange.openapi.spring.SpringEmitter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * The settings are the plugin's whole surface, and resolving them is the only logic it owns.
 * Everything else here delegates to the generator, which has its own tests.
 */
class SettingsResolutionTest :
    FeatureSpec({

        feature("choosing an emitter") {
            scenario("each client kind picks its emitter") {
                ClientKind.Ktorfit.emitter(ModelKind.Auto).shouldBeInstanceOf<KtorfitEmitter>()
                ClientKind.Spring.emitter(ModelKind.Auto).shouldBeInstanceOf<SpringEmitter>()
                ClientKind.None.emitter(ModelKind.Auto).shouldBeInstanceOf<ModelsOnlyEmitter>()
            }
        }

        feature("resolving the model style") {
            scenario("Auto resolves to whatever the caller's client implies") {
                ModelKind.Auto.orElse(ModelStyle.Jackson) shouldBe ModelStyle.Jackson
                ModelKind.Auto.orElse(ModelStyle.Kotlinx) shouldBe ModelStyle.Kotlinx
            }

            scenario("an explicit style wins over the default") {
                ModelKind.Kotlinx.orElse(ModelStyle.Jackson) shouldBe ModelStyle.Kotlinx
                ModelKind.Jackson.orElse(ModelStyle.Kotlinx) shouldBe ModelStyle.Jackson
            }

            scenario("Ktorfit refuses Jackson models rather than silently overriding the request") {
                val error = shouldThrow<IllegalArgumentException> { ClientKind.Ktorfit.emitter(ModelKind.Jackson) }

                error.message shouldContain "kotlinx.serialization"
                error.message shouldContain "client: Spring"
            }

            scenario("Ktorfit accepts the style it was always going to use") {
                ClientKind.Ktorfit.emitter(ModelKind.Kotlinx).shouldBeInstanceOf<KtorfitEmitter>()
            }
        }

        feature("grouping") {
            scenario("every grouping maps onto the parser's own enum") {
                GroupBy.entries.map { it.toGrouping().name } shouldBe GroupBy.entries.map { it.name }
            }
        }
    })
