package com.softistx.r2jdbc

import com.softistx.r2jdbc.sql.numberedPlaceholders
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * The rewrite that lets one statement run on both servers.
 *
 * No container: this is a function over a string, and the half that needs a database — that `?`
 * really does reach both servers as the right thing — is `SqlTest`'s. `DriverContractTest` is where
 * the two spellings are measured against each other.
 */
class PlaceholderTest :
    FeatureSpec({

        fun postgres(sql: String) = sql.numberedPlaceholders(Backend.POSTGRES)

        feature("mysql") {
            scenario("is left exactly as written, because it wanted `?` all along") {
                "select * from t where a = ? and b = ?".numberedPlaceholders(Backend.MYSQL) shouldBe
                    "select * from t where a = ? and b = ?"
            }
        }

        feature("postgres") {
            scenario("numbers each placeholder from one, in the order they appear") {
                postgres("select * from t where a = ? and b = ?") shouldBe
                    "select * from t where a = \$1 and b = \$2"
            }

            scenario("leaves a statement with no placeholders untouched") {
                postgres("select 1") shouldBe "select 1"
            }

            scenario("does not touch a `?` inside a string literal") {
                postgres("select * from t where a = ? and label = 'why? because'") shouldBe
                    "select * from t where a = \$1 and label = 'why? because'"
            }

            scenario("keeps counting past a doubled quote inside a literal") {
                postgres("select 'it''s a ?', ? from t") shouldBe "select 'it''s a ?', \$1 from t"
            }

            scenario("does not touch a `?` inside a quoted identifier") {
                postgres("""select "why?" from t where a = ?""") shouldBe """select "why?" from t where a = ${'$'}1"""
            }

            scenario("does not touch a `?` inside a line comment") {
                postgres("select ? -- and ? here\n, ? from t") shouldBe "select \$1 -- and ? here\n, \$2 from t"
            }

            scenario("does not touch a `?` inside a block comment, nested or not") {
                postgres("select ? /* a ? /* b ? */ c ? */, ? from t") shouldBe
                    "select \$1 /* a ? /* b ? */ c ? */, \$2 from t"
            }

            scenario("does not touch a `?` inside a dollar-quoted body") {
                postgres("select \$fn\$ a ? b \$fn\$, ? from t") shouldBe "select \$fn\$ a ? b \$fn\$, \$1 from t"
            }

            scenario("reads a backslash escape only in an E-string, where Postgres does") {
                // `E'\''` is one literal holding a quote. Without the escape rule the run would end
                // at that quote and the `?` after it would be numbered, which it must not be.
                postgres("select E'\\'?', ? from t") shouldBe "select E'\\'?', \$1 from t"
            }

            scenario("writes `??` as the one literal `?` a jsonb operator needs") {
                postgres("select * from t where doc ?? ? ") shouldBe "select * from t where doc ? \$1 "
            }

            scenario("leaves a `$1` a caller wrote by hand alone, and does not read `$1$2` as a tag") {
                postgres("select \$1, \$2 from t") shouldBe "select \$1, \$2 from t"
                postgres("select \$1\$2 from t") shouldBe "select \$1\$2 from t"
            }
        }
    })
