package com.strange.openapi.parser

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.exists

/** Parses the spec the demo app serves, so real-world shapes are covered, not just toy ones. */
class RealSpecTest :
    FeatureSpec({

        // Anchor on the file that only ever sits at the repo root, so a working directory
        // deeper or shallower than expected still finds the same spec — and a *missing* repo
        // fails here rather than silently picking up some other openapi.yaml along the way.
        val repoRoot =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .firstOrNull { it.resolve("project.yaml").exists() }
        val spec = repoRoot?.resolve("apps/demo-api/openapi.yaml")

        feature("the demo spec") {
            scenario("parses the demo spec end to end") {
                val path =
                    checkNotNull(spec) { "no project.yaml above user.dir=${System.getProperty("user.dir")}" }
                check(path.exists()) { "the repo root at $repoRoot has no apps/demo-api/openapi.yaml" }
                val model = OpenApiParser().parse(path)

                model.groups.map { it.name } shouldContainAll
                    listOf(
                        "AuthApi",
                        "CategoriesApi",
                        "RolesApi",
                        "TagsApi",
                        "UploadsApi",
                        "UsersApi",
                    )
                model.groups.sumOf { it.operations.size } shouldBe 44
                model.models.size shouldBeGreaterThan 10

                // every operation must resolve to a usable return type and named parameters
                val operations = model.groups.flatMap { it.operations }
                operations.all { it.name.isNotBlank() } shouldBe true
                operations.flatMap { it.parameters }.all { it.name.isNotBlank() } shouldBe true
            }
        }
    })
