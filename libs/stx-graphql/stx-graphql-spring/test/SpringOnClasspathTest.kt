package com.strange.graphql.spring

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * The Spring integration is empty this slice — no auto-configuration yet — but the module has
 * to compile against Boot so later slices only add sources.
 */
class SpringOnClasspathTest :
    FeatureSpec({
        feature("spring boot") {
            scenario("spring-boot-autoconfigure is on the module classpath") {
                SpringBootApplication::class.java.shouldNotBeNull()
            }
        }
    })
