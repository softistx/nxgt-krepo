package com.softistx.spring.data.mongo.audit

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.lang.reflect.Method

class AuditListenerIntrospectionTest :
    FeatureSpec({
        feature("introspection without Spring Security") {
            scenario("no declared method of AuditListener mentions a security type") {
                val offending =
                    AuditListener::class.java.declaredMethods
                        .filter { method -> typesOf(method).any { it.name.startsWith("org.springframework.security") } }
                        .map { it.name }

                offending.shouldBeEmpty()
            }
        }
    })

private fun typesOf(method: Method): List<Class<*>> = method.parameterTypes.toList() + method.returnType
