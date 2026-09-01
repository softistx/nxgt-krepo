package com.softistx.openapi.models

import com.softistx.openapi.ApiGroup
import com.softistx.openapi.ApiModel
import com.softistx.openapi.Field
import com.softistx.openapi.ObjectType
import com.softistx.openapi.Operation
import com.softistx.openapi.TypeRef
import com.softistx.openapi.render
import com.softistx.openapi.spring.SpringEmitter
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain

private val MODEL =
    ApiModel(
        groups =
            listOf(
                ApiGroup(
                    name = "OrdersApi",
                    operations =
                        listOf(
                            Operation(
                                id = "findOrder",
                                name = "findOrder",
                                httpMethod = "GET",
                                path = "orders/{id}",
                                parameters = emptyList(),
                                returnType = TypeRef.UnitRef,
                                deprecated = true,
                                deprecatedReason = "Use findOrderV2; this drops the totals block.",
                            ),
                            Operation(
                                id = "listOrders",
                                name = "listOrders",
                                httpMethod = "GET",
                                path = "orders",
                                parameters = emptyList(),
                                returnType = TypeRef.UnitRef,
                                deprecated = true,
                            ),
                        ),
                ),
            ),
        models =
            listOf(
                ObjectType(
                    name = "Order",
                    fields =
                        listOf(
                            Field("id", "id", TypeRef.StringRef, required = true),
                            Field(
                                "legacyRef",
                                "legacy_ref",
                                TypeRef.StringRef,
                                required = false,
                                deprecated = true,
                                deprecatedReason = "Superseded by traceId.",
                            ),
                        ),
                    deprecated = true,
                    deprecatedReason = "Replaced by OrderV2.",
                ),
            ),
    )

/**
 * A document that bothers to say *why* something is deprecated has said the useful half. Carrying
 * only the fact of it and replacing the reason with boilerplate throws that away at the one moment
 * a reader would act on it — the IDE strikethrough.
 */
class DeprecationTest :
    FeatureSpec({

        val files = SpringEmitter().render(MODEL)

        feature("the reason the document gives") {
            scenario("it is the message on the class and on the property") {
                val order = files.getValue("com.example.api.models.Order")

                order shouldContain """@Deprecated("Replaced by OrderV2.")"""
                order shouldContain """@Deprecated("Superseded by traceId.")"""
            }

            scenario("it is the message on the generated function") {
                files.getValue("com.example.api.apis.OrdersApi") shouldContain
                    """@Deprecated("Use findOrderV2; this drops the totals block.")"""
            }
        }

        feature("a deprecation with no reason") {
            scenario("it still marks the declaration, with this generator's own words") {
                files.getValue("com.example.api.apis.OrdersApi") shouldContain
                    """@Deprecated("This operation is deprecated in the OpenAPI document.")"""
            }
        }
    })
