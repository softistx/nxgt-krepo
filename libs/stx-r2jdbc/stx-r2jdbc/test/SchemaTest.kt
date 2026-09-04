package com.softistx.r2jdbc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The schema, which is the half `stx-jpa` could not give a hand-written statement.
 *
 * `@SQLDelete` and `nativeMutate` there send the SQL as written, so an unqualified table name in one
 * resolves against whatever the server's default happens to be — `LegacyDollarNote` fails with
 * *relation "legacy_notes" does not exist* for exactly that reason, and an annotation cannot be
 * fixed because `JpaConfig.schema` is a runtime value and an annotation is a compile-time constant.
 * Here the connection is put in the schema before the caller's first statement.
 */
class SchemaTest :
    FeatureSpec({

        feature("a configured schema").config(enabled = TestPostgres.available) {
            scenario("is where an unqualified table lands") {
                TestPostgres.withSchema { schema ->
                    R2jdbc.connect(TestPostgres.config(schema)).use { db ->
                        db.sql.unprepared("create table unqualified (id int primary key)")

                        // Asked of the catalog rather than of the session: `select` would find the
                        // table through the search path wherever it actually is.
                        db.sql
                            .queryOne(
                                "select table_schema from information_schema.tables where table_name = ?",
                                "unqualified",
                            )?.getString("table_schema") shouldBe schema
                    }
                }
            }

            scenario("is proved at connect, not discovered at the first query") {
                // Postgres accepts `set search_path to "nope"` against a schema that does not exist,
                // so nothing on the connection path would have failed. Without this check the pool
                // would open cleanly and every query after it would say `relation … does not exist`.
                val failure =
                    shouldThrow<R2jdbcException> {
                        R2jdbc.connect(TestPostgres.config("stx_r2jdbc_absent")).use { }
                    }
                failure.message shouldContain "does not exist"
            }
        }

        feature("no schema").config(enabled = TestPostgres.available) {
            scenario("connects nothing until something asks") {
                // The same bargain `Jpa.connect` makes on `SchemaMode.NONE`: a wrong password
                // surfaces at the first statement rather than here. Naming a schema is what buys
                // the eager check above, and this is the other half of that trade.
                R2jdbc
                    .connect(TestPostgres.config(schema = null).copy(password = "not the password"))
                    .use { db -> shouldThrow<Throwable> { db.sql.query("select 1") } }
            }
        }

        feature("a schema on mysql").config(enabled = TestMysql.available) {
            scenario("is the database, and is applied the same way") {
                TestMysql.withR2jdbc { db ->
                    db.sql.unprepared("create table unqualified (id int primary key)")
                    db.sql
                        .queryOne(
                            "select table_schema as ts from information_schema.tables where table_name = ?",
                            "unqualified",
                        )?.getString("ts") shouldBe db.config.schema
                }
            }
        }
    })
