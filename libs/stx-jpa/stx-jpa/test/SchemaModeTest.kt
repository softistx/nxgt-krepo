package com.softistx.jpa

import com.softistx.jpa.entity.Thing
import com.softistx.jpa.session.session
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

/**
 * What [SchemaMode] actually does at `connect`, because two documents disagreed about it.
 *
 * `Jpa.connect`'s KDoc says nothing connects there — the pool opens its first connection when
 * something asks for a session — while the README said `VALIDATE` turns a wrong schema into a
 * startup failure. Schema validation needs a connection, so one of them had to be wrong. This is
 * the spec that settles it, in the module the claim is made about.
 */
class SchemaModeTest :
    FeatureSpec({

        suspend fun connect(
            schema: String,
            mode: SchemaMode,
        ): Jpa =
            Jpa.connect(
                JpaConfig(
                    uri = JpaTestDatabase.endpoint.uri,
                    username = JpaTestDatabase.endpoint.username,
                    password = JpaTestDatabase.endpoint.password,
                    schema = schema,
                    schemaMode = mode,
                ),
                listOf(Thing::class),
            )

        feature("VALIDATE").config(enabled = JpaTestDatabase.available) {
            scenario("connects and fails at startup when the table is not there") {
                JpaTestDatabase.withSchema { schema ->
                    // The schema exists and is empty, so `things` is missing — the shape a deployment
                    // gets when a migration has not been run.
                    val failure = shouldThrowAny { connect(schema, SchemaMode.VALIDATE) }
                    failure.toString() shouldContain "things"
                }
            }

            scenario("connects and succeeds when the schema matches the entities") {
                JpaTestDatabase.withSchema { schema ->
                    connect(schema, SchemaMode.CREATE).close()
                    connect(schema, SchemaMode.VALIDATE).use { it shouldNotBe null }
                }
            }
        }

        feature("NONE").config(enabled = JpaTestDatabase.available) {
            scenario("does not connect, so a missing table is a failed request and not a failed startup") {
                JpaTestDatabase.withSchema { schema ->
                    connect(schema, SchemaMode.NONE).use { jpa ->
                        shouldThrowAny { jpa.session { session -> session.find<Thing>(1L) } }
                    }
                }
            }
        }
    })
