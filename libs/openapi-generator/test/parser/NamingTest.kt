package com.strange.openapi.parser

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class NamingTest :
    FeatureSpec({

        feature("interface names from tags") {
            scenario("a tag becomes an interface name, with the server-side suffix dropped") {
                Naming.interfaceName("categories-controller") shouldBe "CategoriesApi"
                Naming.interfaceName("usersController") shouldBe "UsersApi"
                Naming.interfaceName("users") shouldBe "UsersApi"
            }

            scenario("an unnameable tag still produces something valid") {
                Naming.interfaceName("---") shouldBe "DefaultApi"
            }
        }

        feature("prefix and suffix") {
            scenario("the prefix and suffix are configurable") {
                Naming.interfaceName("categories-controller", InterfaceNaming(prefix = "I")) shouldBe "ICategoriesApi"
                Naming.interfaceName("categories", InterfaceNaming(suffix = "Client")) shouldBe "CategoriesClient"
                Naming.interfaceName("categories", InterfaceNaming(suffix = "")) shouldBe "Categories"
                Naming.interfaceName("categories", InterfaceNaming("I", "Client")) shouldBe "ICategoriesClient"
            }
        }
    })
