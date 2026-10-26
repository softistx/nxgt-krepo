package com.softistx.spring.data.mongo.audit

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty

class AuditListenerIntrospectionTest :
    FeatureSpec({
        feature("no security type in the listener's signatures") {
            scenario("methods, constructors and fields name no org.springframework.security type") {
                val listener = AuditListener::class.java
                val types =
                    listener.declaredMethods.flatMap { it.parameterTypes.toList() + it.returnType } +
                        listener.declaredConstructors.flatMap { it.parameterTypes.toList() } +
                        listener.declaredFields.map { it.type }

                val security = types.map { it.name }
                security.filter { it.startsWith("org.springframework.security") }.shouldBeEmpty()
            }
        }
    })
