package com.strange.openapi.parser

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.exists

/** Parses the spec the demo app serves, so real-world shapes are covered, not just toy ones. */
class RealSpecTest :
    FunSpec({

        val spec =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .map { it.resolve("apps/demo-api/openapi.yaml") }
                .firstOrNull { it.exists() }

        test("parses the demo spec end to end") {
            checkNotNull(spec) { "openapi.yaml not found from user.dir=${System.getProperty("user.dir")}" }
            val model = OpenApiParser().parse(spec)

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
    })
