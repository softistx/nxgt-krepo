package com.softistx.openapi.parser

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

        feature("endpoint constants from a verb and a path") {
            scenario("the verb leads and every separator becomes an underscore") {
                Naming.endpointConstant("GET", "orders") shouldBe "GET_ORDERS"
                Naming.endpointConstant("PATCH", "orders/{id}/status") shouldBe "PATCH_ORDERS_ID_STATUS"
                Naming.endpointConstant("DELETE", "orders/{orderId}") shouldBe "DELETE_ORDERS_ORDER_ID"
            }

            scenario("a leading or trailing slash changes nothing, because it is a separator too") {
                Naming.endpointConstant("GET", "/orders/") shouldBe "GET_ORDERS"
                Naming.endpointConstant("GET", "orders") shouldBe "GET_ORDERS"
            }

            scenario("the root path is the verb alone rather than a dangling underscore") {
                Naming.endpointConstant("GET", "") shouldBe "GET"
                Naming.endpointConstant("GET", "/") shouldBe "GET"
            }

            scenario("a segment that starts with a digit is prefixed, since a Kotlin name may not") {
                // The `V` comes from `enumEntry`, which this shares — a path like `/2fa/verify` is
                // an ordinary thing for a document to declare and an illegal thing to name.
                Naming.endpointConstant("POST", "2fa/verify") shouldBe "POST_2FA_VERIFY"
            }

            scenario("a template variable and a literal segment reduce alike, which is the collision") {
                // Not a bug to fix here but the reason `requireDistinctEndpointConstants` exists:
                // treating a brace as punctuation is what makes the name readable in the first place.
                Naming.endpointConstant("GET", "orders/{id}") shouldBe
                    Naming.endpointConstant("GET", "orders/id")
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
