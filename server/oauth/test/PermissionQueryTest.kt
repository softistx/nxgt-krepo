package com.softistx.oauth

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixRequest
import com.softistx.oauth.controllers.PermissionController
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * The first spec in this module, and the reason it exists.
 *
 * `permissions` is declared `[Permission!]!` in `resources/graphql/permissions.graphqls` and served
 * by a resolver returning `Flow<Permission>`. On the SDL path the document owns the type and the
 * Kotlin return type is read by nobody, so a mismatch between the two builds cleanly and fails only
 * when a client asks — which is how this shipped answering
 * `Can't resolve value (/permissions) : type mismatch error, expected type LIST`.
 *
 * The engine is built here rather than through `install(GraphQL)` on purpose: what is being proved
 * is the schema and the resolvers, and booting Ktor, Koin and a Mongo client to prove it would test
 * the wiring instead.
 */
class PermissionQueryTest :
    FeatureSpec({
        feature("the permissions query") {
            scenario("a resolver returning a Flow answers the list the SDL promises") {
                val graphql = Graphix { resolvers(PermissionController()) }

                val data = graphql.execute(GraphixRequest("{ permissions { id name } }")).data

                data?.get("permissions") shouldBe
                    listOf(
                        mapOf("id" to "1", "name" to "read"),
                        mapOf("id" to "2", "name" to "write"),
                        mapOf("id" to "3", "name" to "delete"),
                    )
            }
        }
    })
