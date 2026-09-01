package com.softistx.openapi.plugin

import com.softistx.openapi.ktorfit.KtorfitEmitter
import com.softistx.openapi.models.ModelStyle
import com.softistx.openapi.models.ModelsOnlyEmitter
import com.softistx.openapi.spring.SpringEmitter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path
import kotlin.io.path.createTempFile

/**
 * The settings are the plugin's whole surface, and resolving them is the only logic it owns.
 * Everything else here delegates to the generator, which has its own tests.
 */
class SettingsResolutionTest :
    FeatureSpec({

        // `SpecSettings` is an interface the toolchain implements from YAML; this is the same shape
        // by hand, so the guards can be driven without a build.
        fun settings(
            spec: Path,
            packageName: String = "com.example.api",
        ) = object : SpecSettings {
            override val spec: Path = spec
            override val packageName: String = packageName
            override val client: ClientKind = ClientKind.None
            override val groupBy: GroupBy = GroupBy.Tag
            override val models: ModelKind = ModelKind.Auto
            override val interfacePrefix: String = ""
            override val interfaceSuffix: String = "Api"
        }

        val document = createTempFile("probe", ".yaml")

        feature("what is refused before a byte is written") {
            scenario("an enabled plugin listing no document is a mistake, not a no-op") {
                val error = shouldThrow<IllegalArgumentException> { emptyList<SpecSettings>().validate() }

                error.message shouldContain "`specs` is empty"
            }

            scenario("two documents sharing a package, which nothing downstream would catch") {
                // They write into one directory, so the second overwrites the first file for file —
                // and `writeAllTo`'s duplicate check only spans one document's own files.
                val error =
                    shouldThrow<IllegalStateException> {
                        listOf(
                            settings(document, packageName = "com.example.api"),
                            settings(document, packageName = "com.example.api"),
                        ).validate()
                    }

                error.message shouldContain "com.example.api"
                error.message shouldContain "overwrite each other"
            }

            scenario("a document that is not there names itself") {
                val error =
                    shouldThrow<IllegalStateException> {
                        listOf(settings(Path.of("does-not-exist.yaml"))).validate()
                    }

                error.message shouldContain "spec file not found"
            }

            scenario("a blank packageName") {
                val error =
                    shouldThrow<IllegalArgumentException> {
                        listOf(settings(document, packageName = " ")).validate()
                    }

                error.message shouldContain "packageName must not be blank"
            }

            scenario("two documents in two packages are exactly the point, and pass") {
                listOf(
                    settings(document, packageName = "com.example.orders"),
                    settings(document, packageName = "com.example.billing"),
                ).validate()
            }
        }

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
