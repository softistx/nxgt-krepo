package com.strange.openapi.spring

import com.strange.openapi.ApiModel
import com.strange.openapi.EnumEntry
import com.strange.openapi.EnumType
import com.strange.openapi.ObjectType
import com.strange.openapi.TypeRef
import com.strange.openapi.render
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.string.shouldContain

private val WITH_ENUMS =
    ApiModel(
        groups = emptyList(),
        models =
            listOf(
                EnumType(
                    name = "Status",
                    entries = listOf(EnumEntry("IN_PROGRESS", "in-progress")),
                    base = TypeRef.StringRef,
                    fallback = EnumEntry("UNKNOWN", "__unknown__"),
                ),
                ObjectType(name = "Order", fields = emptyList()),
            ),
    )

/**
 * Spring converts an enum argument with `Enum.name()`, so `IN_PROGRESS` goes out where the document
 * says `in-progress` — a request that succeeds and matches nothing. The generated registration is
 * the only part of the fix this generator can own; the line that hands it to the proxy factory
 * belongs to whoever builds the factory.
 */
class EnumConverterTest :
    FeatureSpec({

        feature("the converter registration") {
            scenario("every generated enum is registered, as its wire value") {
                val source = SpringEmitter().render(WITH_ENUMS).getValue("com.example.api.utils.ApiEnumConverters")

                source shouldContain "public fun registerApiEnumConverters(registry: ConverterRegistry)"
                source shouldContain "registry.addConverter(Status::class.java, String::class.java) { it.toString() }"
            }

            scenario("a document with no enums gets no file to wire up") {
                SpringEmitter()
                    .render(ApiModel(groups = emptyList(), models = listOf(ObjectType("Order", emptyList()))))
                    .shouldNotContainKey("com.example.api.utils.ApiEnumConverters")
            }
        }
    })
