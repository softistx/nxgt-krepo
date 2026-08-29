package com.strange.common.http

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CorsPolicyTest :
    StringSpec({
        "a policy permits nobody until it is told who" {
            // A browser policy that arrives already permitting somebody is the wrong shape of
            // default, and this is the one field with no safe permissive value.
            CorsPolicy().origins shouldBe emptyList()
        }

        "everything else defaults permissively" {
            // Once an origin is trusted, restricting which methods it may use adds nothing an
            // attacker at that origin cannot work around anyway.
            CorsPolicy().methods shouldBe listOf("*")
            CorsPolicy().headers shouldBe listOf("*")
        }

        "a wildcard origin with credentials is refused, and told what to use instead" {
            val failure =
                shouldThrow<IllegalArgumentException> {
                    CorsPolicy(origins = listOf("*"), allowCredentials = true).validate()
                }

            failure.message!! shouldContain "originPatterns"
        }

        "the message names the setting in the caller's own vocabulary" {
            // What to do instead is the useful half of this failure, and it is spelled differently
            // depending on where the policy was written.
            val failure =
                shouldThrow<IllegalArgumentException> {
                    CorsPolicy(origins = listOf("*")).validate(patternsSetting = "stx.cors.origin-patterns")
                }

            failure.message!! shouldContain "stx.cors.origin-patterns"
        }

        "a wildcard origin without credentials is fine" {
            CorsPolicy(origins = listOf("*"), allowCredentials = false).validate().origins shouldBe listOf("*")
        }

        "origin patterns are how a wildcard and credentials go together" {
            CorsPolicy(originPatterns = listOf("https://sub.example.com"), allowCredentials = true)
                .validate()
                .allowCredentials shouldBe true
        }

        "validate answers the policy, so it composes into a builder" {
            val policy = CorsPolicy(origins = listOf("http://localhost:5173"))

            policy.validate() shouldBe policy
        }
    })
