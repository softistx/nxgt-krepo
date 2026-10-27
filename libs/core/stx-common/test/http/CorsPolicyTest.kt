package com.softistx.common.http

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CorsPolicyTest :
    FeatureSpec({
        feature("defaults") {
            scenario("a policy permits nobody until it is told who") {
                // A browser policy that arrives already permitting somebody is the wrong shape of
                // default, and this is the one field with no safe permissive value.
                CorsPolicy().origins shouldBe emptyList()
            }

            scenario("everything else defaults permissively") {
                // Once an origin is trusted, restricting which methods it may use adds nothing an
                // attacker at that origin cannot work around anyway.
                CorsPolicy().methods shouldBe listOf("*")
                CorsPolicy().headers shouldBe listOf("*")
            }
        }

        feature("wildcard origins") {
            scenario("a wildcard origin with credentials is refused, and told what to use instead") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        CorsPolicy(origins = listOf("*"), allowCredentials = true).validate()
                    }

                failure.message!! shouldContain "originPatterns"
            }

            scenario("the message names the setting in the caller's own vocabulary") {
                // What to do instead is the useful half of this failure, and it is spelled differently
                // depending on where the policy was written.
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        CorsPolicy(origins = listOf("*")).validate(patternsSetting = "stx.cors.origin-patterns")
                    }

                failure.message!! shouldContain "stx.cors.origin-patterns"
            }

            scenario("a wildcard origin without credentials is fine") {
                CorsPolicy(origins = listOf("*"), allowCredentials = false).validate().origins shouldBe listOf("*")
            }

            scenario("origin patterns are how a wildcard and credentials go together") {
                CorsPolicy(originPatterns = listOf("https://sub.example.com"), allowCredentials = true)
                    .validate()
                    .allowCredentials shouldBe true
            }
        }

        feature("validation") {
            scenario("validate answers the policy, so it composes into a builder") {
                val policy = CorsPolicy(origins = listOf("http://localhost:5173"))

                policy.validate() shouldBe policy
            }

            scenario("an origin without its scheme is refused, whichever framework built the policy") {
                val failure = shouldThrow<IllegalArgumentException> { CorsPolicy(origins = listOf("example.com")).validate() }

                failure.message!! shouldContain "https://example.com"
                CorsPolicy(origins = listOf("https://example.com", "*"), allowCredentials = false).validate()
            }
        }
    })
