package com.softistx.openapi.parser

import com.softistx.openapi.SecurityKind
import com.softistx.openapi.SecurityRequirement
import com.softistx.openapi.TypeRef
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
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
        val spec = repoRoot?.resolve("examples/demo-api/openapi.yaml")

        fun specPath(): Path {
            val path = checkNotNull(spec) { "no project.yaml above user.dir=${System.getProperty("user.dir")}" }
            check(path.exists()) { "the repo root at $repoRoot has no examples/demo-api/openapi.yaml" }
            return path
        }

        feature("endpoint constants over a real document") {
            scenario("every operation gets one, and no two collide") {
                // 53 operations across 10 tags is where a derived name actually gets tested: the
                // parser would have thrown on a collision, so reaching the assertion is half of it,
                // and the count proves none were quietly merged away.
                val model = OpenApiParser().parse(specPath())
                val operations = model.groups.flatMap { it.operations }
                val constants = operations.map { it.constant }

                constants.distinct().size shouldBe operations.size
                constants.forEach { it shouldMatch Regex("[A-Z][A-Z0-9_]*") }
                // `refresh-token` is the one worth naming: a kebab segment is where a derived
                // constant could plausibly come out unusable, and it reads POST_AUTH_REFRESH_TOKEN.
                constants shouldContainAll listOf("POST_AUTH_LOGIN", "POST_AUTH_REFRESH_TOKEN")
            }
        }

        feature("the demo spec") {
            scenario("parses the demo spec end to end") {
                val path =
                    checkNotNull(spec) { "no project.yaml above user.dir=${System.getProperty("user.dir")}" }
                check(path.exists()) { "the repo root at $repoRoot has no examples/demo-api/openapi.yaml" }
                val model = OpenApiParser().parse(path)

                model.groups.map { it.name } shouldContainAll
                    listOf(
                        "AuthApi",
                        "CategoriesApi",
                        "NotificationsApi",
                        "RolesApi",
                        "TagsApi",
                        "UploadsApi",
                        "UsersApi",
                    )
                // The document declares 54; `purgeNotifications` is marked x-internal, so a
                // generated client is one operation smaller than the document it came from.
                model.groups.sumOf { it.operations.size } shouldBe 53
                operationsOf(model).none { it.name == "purgeNotifications" } shouldBe true
                model.models.size shouldBeGreaterThan 10

                // every operation must resolve to a usable return type and named parameters
                val operations = operationsOf(model)
                operations.all { it.name.isNotBlank() } shouldBe true
                operations.flatMap { it.parameters }.all { it.name.isNotBlank() } shouldBe true
            }

            scenario("its declared failures are read, not discarded") {
                val operations = operationsOf(OpenApiParser().parse(specPath()))

                // Every one of these is a `$ref` into components/responses, so this count is also
                // the check that they are followed rather than skipped.
                operations.sumOf { it.errors.size } shouldBe 172
                // Not every operation declares one: the two `/notifications` reads and
                // `/session/echo` do not, and a document is entitled to say nothing about how an
                // operation fails.
                operations.count { it.errors.isEmpty() } shouldBe 5
                // One error schema throughout, which is what makes one generated exception enough.
                operations
                    .flatMap { it.errors }
                    .mapNotNull { it.type }
                    .toSet() shouldBe setOf(TypeRef.ModelRef("ErrorResponse"))
            }

            scenario("its root security and the operations that opt out of it are read") {
                val model = OpenApiParser().parse(specPath())
                val operations = operationsOf(model)

                model.securitySchemes.map { it.name to it.kind } shouldContainAll
                    listOf("Basic" to SecurityKind.HttpBasic, "Bearer" to SecurityKind.HttpBearer)
                // The document's root is `security: - Bearer: []`, which 37 operations inherit;
                // 14 override it with `security: []`, and `/session/either` and `/session/optional`
                // list alternatives of their own.
                val bearer = listOf(SecurityRequirement("Bearer"))
                val basic = listOf(SecurityRequirement("Basic"))
                operations.count { it.security.isEmpty() } shouldBe 14
                operations.count { it.security == listOf(bearer) } shouldBe 37
                operations.single { it.name == "echoEither" }.security shouldBe listOf(bearer, basic)
                operations.single { it.name == "echoOptional" }.security shouldBe listOf(bearer, emptyList())
            }
        }
    })

private fun operationsOf(model: com.softistx.openapi.ApiModel) = model.groups.flatMap { it.operations }
